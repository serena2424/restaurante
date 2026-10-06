package com.barclub.repository;

import com.barclub.entity.Usuario;
import com.barclub.entity.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    boolean existsByEmail(String email);
    long countByRol(Rol rol);

    // Usuarios activos de un rol (null cuenta como activo: filas de antes de
    // que existiera la columna).
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(u) FROM Usuario u WHERE u.rol = :rol AND (u.activo IS NULL OR u.activo = true)")
    long countActivosByRol(@org.springframework.data.repository.query.Param("rol") Rol rol);
}
