package org.example.kalkulationsprogramm.service.ablage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.rapid7.client.dcerpc.mssrvs.ServerService;
import com.rapid7.client.dcerpc.mssrvs.dto.NetShareInfo0;
import com.rapid7.client.dcerpc.transport.RPCTransport;
import com.rapid7.client.dcerpc.transport.SMBTransportFactories;

/**
 * Zeigt, was auf einem Gerät zur Auswahl steht: zuerst die Freigaben, dann die Ordner darin. So muss man den Namen der Freigabe nicht kennen.
 * Es wird nichts verändert.
 */
public class SmbDurchsucher {

    private static SMBClient neuerClient() {
        return new SMBClient(SmbConfig.builder().withTimeout(15, TimeUnit.SECONDS).withSoTimeout(30, TimeUnit.SECONDS).build());
    }

    /** Die Freigaben des Geräts ohne die versteckten (Namen mit {@code $}), alphabetisch. */
    public List<String> freigaben(SmbAbgleichEinstellungen e) throws IOException {
        try (SMBClient client = neuerClient(); Connection verbindung = client.connect(e.server().trim())) {
            Session sitzung = SmbjAbgleichSpeicher.anmelden(verbindung, e);
            RPCTransport transport = SMBTransportFactories.SRVSVC.getTransport(sitzung);
            List<String> namen = new ArrayList<>();
            for (NetShareInfo0 s : new ServerService(transport).getShares0()) {
                String name = s.getNetName();
                if (name != null && !name.isBlank() && !name.endsWith("$")) {
                    namen.add(name);
                }
            }
            namen.sort(String.CASE_INSENSITIVE_ORDER);
            return namen;
        }
    }

    /** Die Unterordner eines Ordners in der Freigabe ({@code pfad} leer = oberste Ebene), ohne System- und versteckte Ordner. */
    public List<String> ordner(SmbAbgleichEinstellungen e, String freigabe, String pfad) throws IOException {
        String sauber = pfad == null ? "" : pfad.trim().replace('/', '\\').replaceAll("^\\\\+|\\\\+$", "");
        try (SMBClient client = neuerClient(); Connection verbindung = client.connect(e.server().trim())) {
            Session sitzung = SmbjAbgleichSpeicher.anmelden(verbindung, e);
            try (DiskShare share = (DiskShare) sitzung.connectShare(freigabe.trim())) {
                List<String> namen = new ArrayList<>();
                for (FileIdBothDirectoryInformation i : share.list(sauber)) {
                    String name = i.getFileName();
                    boolean istOrdner = (i.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0;
                    boolean versteckt = (i.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_HIDDEN.getValue()) != 0;
                    if (istOrdner && !versteckt && !".".equals(name) && !"..".equals(name) && !name.startsWith(".") && !name.startsWith("@") && !name.startsWith("#")
                            && !name.toLowerCase(Locale.ROOT).equals("$recycle.bin") && !name.toLowerCase(Locale.ROOT).equals("system volume information")) {
                        namen.add(name);
                    }
                }
                namen.sort(Comparator.comparing(String::toLowerCase));
                return namen;
            }
        }
    }
}
