package com.example.cabinets.cabinet;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CabinetRepository extends JpaRepository<Cabinet, Long> {

	boolean existsBySiren(String siren);

	boolean existsBySirenAndIdNot(String siren, Long id);

}
