package org.example.kalkulationsprogramm.repository.wartung;

import org.example.kalkulationsprogramm.domain.wartung.WartungGlobalesDatenfeld;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WartungGlobalesDatenfeldRepository extends JpaRepository<WartungGlobalesDatenfeld, Long> {

    List<WartungGlobalesDatenfeld> findAllByOrderBySortierungAscIdAsc();
}
