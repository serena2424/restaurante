package com.gestorgastronomico.service;

import com.gestorgastronomico.dto.ClienteRequestDTO;
import com.gestorgastronomico.dto.ClienteResponseDTO;
import com.gestorgastronomico.entity.Cliente;
import com.gestorgastronomico.exception.BusinessException;
import com.gestorgastronomico.exception.ResourceNotFoundException;
import com.gestorgastronomico.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ClienteService {

    private final ClienteRepository clienteRepository;

    @Transactional(readOnly = true)
    public List<ClienteResponseDTO> listarTodos() {
        return clienteRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClienteResponseDTO obtenerPorId(Long id) {
        return toDTO(clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", id)));
    }

    @Transactional(readOnly = true)
    public List<ClienteResponseDTO> buscarPorNombre(String nombre) {
        return clienteRepository.findByNombreContainingIgnoreCase(nombre)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public ClienteResponseDTO crear(ClienteRequestDTO dto) {
        Cliente cliente = Cliente.builder()
                .nombre(dto.getNombre())
                .telefono(dto.getTelefono())
                .direccion(dto.getDireccion())
                .build();
        return toDTO(clienteRepository.save(cliente));
    }

    public ClienteResponseDTO actualizar(Long id, ClienteRequestDTO dto) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", id));
        cliente.setNombre(dto.getNombre());
        cliente.setTelefono(dto.getTelefono());
        cliente.setDireccion(dto.getDireccion());
        return toDTO(clienteRepository.save(cliente));
    }

    public void eliminar(Long id) {
        if (!clienteRepository.existsById(id)) {
            throw new ResourceNotFoundException("Cliente", id);
        }
        try {
            clienteRepository.deleteById(id);
            clienteRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("Este cliente tiene pedidos o reservas registrados, así que no se puede borrar sin perder el historial.");
        }
    }

    public ClienteResponseDTO toDTO(Cliente c) {
        return ClienteResponseDTO.builder()
                .id(c.getId())
                .nombre(c.getNombre())
                .telefono(c.getTelefono())
                .direccion(c.getDireccion())
                .build();
    }
}
