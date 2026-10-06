package com.gestorgastronomico.repository;

import com.gestorgastronomico.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findByActivoTrue();
    List<Producto> findByCategoria(String categoria);
    List<Producto> findByCategoriaAndActivoTrue(String categoria);
    List<Producto> findByNombreContainingIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndCategoria(String nombre, String categoria);
    boolean existsByNombreIgnoreCaseAndCategoriaAndIdNot(String nombre, String categoria, Long id);
}
