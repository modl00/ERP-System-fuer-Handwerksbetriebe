package org.example.kalkulationsprogramm.service.ablage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileAllInformation;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;

/** Abgleich mit einer Netzwerkfreigabe über SMB (Bibliothek smbj). Nichts wird im Betriebssystem eingebunden; es braucht keine Sonderrechte. */
public class SmbjAbgleichSpeicher implements AbgleichSpeicher {

    private static final String INTERN = "_erp";
    /** Größere Dateien werden nicht in den Arbeitsspeicher geladen. */
    static final long MAX_BYTES = 100L * 1024 * 1024;

    private final SMBClient client;
    private final Connection verbindung;
    private final Session sitzung;
    private final DiskShare freigabe;
    private final String basis;

    public SmbjAbgleichSpeicher(SmbAbgleichEinstellungen e) throws IOException {
        this.client = new SMBClient(SmbConfig.builder().withTimeout(15, TimeUnit.SECONDS).withSoTimeout(30, TimeUnit.SECONDS).build());
        try {
            this.verbindung = client.connect(e.server().trim());
            this.sitzung = anmelden(verbindung, e);
            this.freigabe = (DiskShare) sitzung.connectShare(e.freigabe().trim());
            this.basis = e.unterordnerSauber().replace('/', '\\');
            legeOrdnerAn(basis);
        } catch (Exception ex) {
            client.close();
            throw ex;
        }
    }

    static Session anmelden(Connection v, SmbAbgleichEinstellungen e) throws IOException {
        if (e.benutzer() == null || e.benutzer().isBlank()) {
            // Ohne Benutzer: zuerst als Gast, dann anonym
            try {
                return v.authenticate(AuthenticationContext.guest());
            } catch (RuntimeException gast) {
                return v.authenticate(AuthenticationContext.anonymous());
            }
        }
        char[] pw = e.passwort() == null ? new char[0] : e.passwort().toCharArray();
        return v.authenticate(new AuthenticationContext(e.benutzer().trim(), pw, e.domaene() == null ? "" : e.domaene().trim()));
    }

    private String voll(String relativ) {
        String r = relativ.replace('/', '\\');
        return basis.isEmpty() ? r : basis + "\\" + r;
    }

    private void legeOrdnerAn(String ordner) {
        if (ordner.isEmpty()) {
            return;
        }
        StringBuilder pfad = new StringBuilder();
        for (String teil : ordner.split("\\\\")) {
            if (teil.isEmpty()) {
                continue;
            }
            pfad.append(pfad.length() == 0 ? "" : "\\").append(teil);
            if (!freigabe.folderExists(pfad.toString())) {
                freigabe.mkdir(pfad.toString());
            }
        }
    }

    @Override
    public List<Datei> liste() throws IOException {
        List<Datei> ergebnis = new ArrayList<>();
        durchsuche(basis, "", ergebnis);
        return ergebnis;
    }

    private void durchsuche(String ordnerVoll, String ordnerRel, List<Datei> ergebnis) {
        for (FileIdBothDirectoryInformation i : freigabe.list(ordnerVoll)) {
            String name = i.getFileName();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            String rel = ordnerRel.isEmpty() ? name : ordnerRel + "/" + name;
            if (ordnerRel.isEmpty() && INTERN.equalsIgnoreCase(name)) {
                continue;
            }
            boolean istOrdner = (i.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0;
            if (istOrdner) {
                durchsuche(ordnerVoll.isEmpty() ? name : ordnerVoll + "\\" + name, rel, ergebnis);
            } else {
                ergebnis.add(new Datei(rel, i.getEndOfFile(), i.getLastWriteTime().toEpochMillis()));
            }
        }
    }

    @Override
    public Optional<Datei> info(String relativ) throws IOException {
        String p = voll(relativ);
        if (!freigabe.fileExists(p)) {
            return Optional.empty();
        }
        FileAllInformation i = freigabe.getFileInformation(p);
        return Optional.of(new Datei(relativ, i.getStandardInformation().getEndOfFile(), i.getBasicInformation().getLastWriteTime().toEpochMillis()));
    }

    @Override
    public byte[] lies(String relativ) throws IOException {
        Optional<Datei> d = info(relativ);
        if (d.isPresent() && d.get().groesse() > MAX_BYTES) {
            throw new IOException("Die Datei ist größer als 100 MB und wird nicht übertragen: " + relativ);
        }
        try (File f = freigabe.openFile(voll(relativ), EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null);
                InputStream in = f.getInputStream()) {
            return in.readAllBytes();
        }
    }

    @Override
    public void schreibe(String relativ, byte[] inhalt) throws IOException {
        String p = voll(relativ);
        int ende = p.lastIndexOf('\\');
        if (ende > 0) {
            legeOrdnerAn(p.substring(0, ende));
        }
        try (File f = freigabe.openFile(p, EnumSet.of(AccessMask.GENERIC_WRITE), EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL), SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF, null);
                OutputStream out = f.getOutputStream()) {
            out.write(inhalt);
        }
    }

    @Override
    public void close() {
        try {
            freigabe.close();
        } catch (Exception ignoriert) {
            // wird beendet
        }
        try {
            sitzung.close();
        } catch (Exception ignoriert) {
            // wird beendet
        }
        try {
            verbindung.close();
        } catch (Exception ignoriert) {
            // wird beendet
        }
        client.close();
    }
}
