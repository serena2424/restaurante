package com.gestorgastronomico.repository;

import com.gestorgastronomico.entity.ConfigLocal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfigLocalRepository extends JpaRepository<ConfigLocal, Long> {
}
