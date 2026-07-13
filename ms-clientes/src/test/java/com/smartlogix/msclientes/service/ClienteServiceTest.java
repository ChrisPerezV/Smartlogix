package com.smartlogix.msclientes.service;

import com.smartlogix.msclientes.dto.*;
import com.smartlogix.msclientes.model.Cliente;
import com.smartlogix.msclientes.model.Comuna;
import com.smartlogix.msclientes.model.DireccionCliente;
import com.smartlogix.msclientes.model.Provincia;
import com.smartlogix.msclientes.model.Region;
import com.smartlogix.msclientes.repository.ClienteRepository;
import com.smartlogix.msclientes.repository.ComunaRepository;
import com.smartlogix.msclientes.repository.DireccionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClienteService - Tests unitarios")
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private DireccionRepository direccionRepository;
    @Mock
    private ComunaRepository comunaRepository;

    @InjectMocks
    private ClienteService clienteService;

    private Cliente clienteBase;
    private DireccionCliente direccionBase;
    private Comuna comunaBase;
    private Region regionBase;
    private Provincia provinciaBase;

    @BeforeEach
    void setUp() {
        regionBase = new Region();
        regionBase.setIdRegion(1);
        regionBase.setNombreRegion("Región Metropolitana");
        regionBase.setCodigoRegion("RM");

        provinciaBase = new Provincia();
        provinciaBase.setIdProvincia(1);
        provinciaBase.setNombreProvincia("Santiago");
        provinciaBase.setRegion(regionBase);

        comunaBase = new Comuna();
        comunaBase.setIdComuna(1);
        comunaBase.setNombreComuna("Santiago");
        comunaBase.setProvincia(provinciaBase);

        clienteBase = Cliente.builder()
                .idCliente(1L)
                .idUsuarioAuth(100L)
                .rut("12345678-9")
                .nombre("Juan")
                .apellidoPaterno("Pérez")
                .apellidoMaterno("González")
                .correo("juan@test.com")
                .telefono("+56912345678")
                .build();

        direccionBase = DireccionCliente.builder()
                .idDireccion(1L)
                .cliente(clienteBase)
                .comuna(comunaBase)
                .calle("Av. Libertad")
                .numero("123")
                .detalle("Depto 4")
                .esPrincipal(true)
                .build();
    }

    // ── listar ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listar() retorna lista de clientes mapeados")
    void listar_retornaClientesMapeados() {
        when(clienteRepository.findAll()).thenReturn(List.of(clienteBase));
        when(direccionRepository.findByClienteIdCliente(1L)).thenReturn(List.of(direccionBase));

        List<ClienteResponse> resultado = clienteService.listar();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getRut()).isEqualTo("12345678-9");
        assertThat(resultado.get(0).getNombre()).isEqualTo("Juan");
    }

    @Test
    @DisplayName("listar() retorna lista vacía cuando no hay clientes")
    void listar_retornaListaVacia() {
        when(clienteRepository.findAll()).thenReturn(List.of());

        List<ClienteResponse> resultado = clienteService.listar();

        assertThat(resultado).isEmpty();
    }

    // ── buscarPorIdDto ─────────────────────────────────────────────────────

    @Test
    @DisplayName("buscarPorIdDto() retorna cliente existente")
    void buscarPorIdDto_retornaCliente() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteBase));
        when(direccionRepository.findByClienteIdCliente(1L)).thenReturn(List.of(direccionBase));

        ClienteResponse resultado = clienteService.buscarPorIdDto(1L);

        assertThat(resultado.getIdCliente()).isEqualTo(1L);
        assertThat(resultado.getCorreo()).isEqualTo("juan@test.com");
    }

    @Test
    @DisplayName("buscarPorIdDto() lanza 404 si no existe")
    void buscarPorIdDto_noExiste_lanza404() {
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.buscarPorIdDto(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no encontrado");
    }

    // ── buscarPorCorreoDto ─────────────────────────────────────────────────

    @Test
    @DisplayName("buscarPorCorreoDto() retorna cliente por correo")
    void buscarPorCorreoDto_retornaCliente() {
        when(clienteRepository.findByCorreo("juan@test.com")).thenReturn(Optional.of(clienteBase));
        when(direccionRepository.findByClienteIdCliente(1L)).thenReturn(List.of());

        ClienteResponse resultado = clienteService.buscarPorCorreoDto("juan@test.com");

        assertThat(resultado.getCorreo()).isEqualTo("juan@test.com");
    }

    @Test
    @DisplayName("buscarPorCorreoDto() lanza 404 si correo no existe")
    void buscarPorCorreoDto_noExiste_lanza404() {
        when(clienteRepository.findByCorreo("noexiste@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.buscarPorCorreoDto("noexiste@test.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no encontrado");
    }

    // ── crearDesdeAuth ─────────────────────────────────────────────────────

    @Test
    @DisplayName("crearDesdeAuth() crea cliente exitosamente")
    void crearDesdeAuth_creaClienteOk() {
        DireccionPrincipalRequest dirReq = new DireccionPrincipalRequest();
        dirReq.setIdComuna(1);
        dirReq.setCalle("Av. Libertad");
        dirReq.setNumero("123");
        dirReq.setDetalle("Depto 4");

        CrearClienteDesdeAuthRequest request = new CrearClienteDesdeAuthRequest();
        request.setIdUsuarioAuth(100L);
        request.setRut("12345678-9");
        request.setNombre("Juan");
        request.setApellidoPaterno("Pérez");
        request.setApellidoMaterno("González");
        request.setCorreo("juan@test.com");
        request.setTelefono("+56912345678");
        request.setDireccionPrincipal(dirReq);

        when(clienteRepository.findByIdUsuarioAuth(100L)).thenReturn(Optional.empty());
        when(clienteRepository.findByCorreo("juan@test.com")).thenReturn(Optional.empty());
        when(clienteRepository.findByRut("12345678-9")).thenReturn(Optional.empty());
        when(comunaRepository.findById(1)).thenReturn(Optional.of(comunaBase));
        when(clienteRepository.save(any(Cliente.class))).thenReturn(clienteBase);
        when(direccionRepository.save(any(DireccionCliente.class))).thenReturn(direccionBase);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteBase));
        when(direccionRepository.findByClienteIdCliente(1L)).thenReturn(List.of(direccionBase));

        ClienteResponse resultado = clienteService.crearDesdeAuth(request);

        assertThat(resultado.getRut()).isEqualTo("12345678-9");
        assertThat(resultado.getNombre()).isEqualTo("Juan");
        verify(clienteRepository).save(any(Cliente.class));
        verify(direccionRepository).save(any(DireccionCliente.class));
    }

    @Test
    @DisplayName("crearDesdeAuth() lanza CONFLICT si ya existe el idUsuarioAuth")
    void crearDesdeAuth_conflictoUsuarioAuth() {
        CrearClienteDesdeAuthRequest request = new CrearClienteDesdeAuthRequest();
        request.setIdUsuarioAuth(100L);
        request.setRut("12345678-9");
        request.setCorreo("juan@test.com");
        request.setDireccionPrincipal(new DireccionPrincipalRequest());

        when(clienteRepository.findByIdUsuarioAuth(100L)).thenReturn(Optional.of(clienteBase));

        assertThatThrownBy(() -> clienteService.crearDesdeAuth(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ya existe un cliente");
    }

    @Test
    @DisplayName("crearDesdeAuth() lanza CONFLICT si ya existe el correo")
    void crearDesdeAuth_conflictoCorreo() {
        CrearClienteDesdeAuthRequest request = new CrearClienteDesdeAuthRequest();
        request.setIdUsuarioAuth(200L);
        request.setRut("99999999-9");
        request.setCorreo("juan@test.com");
        request.setDireccionPrincipal(new DireccionPrincipalRequest());

        when(clienteRepository.findByIdUsuarioAuth(200L)).thenReturn(Optional.empty());
        when(clienteRepository.findByCorreo("juan@test.com")).thenReturn(Optional.of(clienteBase));

        assertThatThrownBy(() -> clienteService.crearDesdeAuth(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("correo");
    }

    @Test
    @DisplayName("crearDesdeAuth() lanza CONFLICT si ya existe el RUT")
    void crearDesdeAuth_conflictoRut() {
        CrearClienteDesdeAuthRequest request = new CrearClienteDesdeAuthRequest();
        request.setIdUsuarioAuth(200L);
        request.setRut("12345678-9");
        request.setCorreo("otro@test.com");
        request.setDireccionPrincipal(new DireccionPrincipalRequest());

        when(clienteRepository.findByIdUsuarioAuth(200L)).thenReturn(Optional.empty());
        when(clienteRepository.findByCorreo("otro@test.com")).thenReturn(Optional.empty());
        when(clienteRepository.findByRut("12345678-9")).thenReturn(Optional.of(clienteBase));

        assertThatThrownBy(() -> clienteService.crearDesdeAuth(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("RUT");
    }

    // ── actualizar ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("actualizar() modifica datos del cliente")
    void actualizar_modificaCliente() {
        CrearClienteDesdeAuthRequest request = new CrearClienteDesdeAuthRequest();
        request.setNombre("Pedro");
        request.setApellidoPaterno("Soto");
        request.setApellidoMaterno("Díaz");
        request.setCorreo("pedro@test.com");
        request.setTelefono("+56998765432");
        request.setDireccionPrincipal(new DireccionPrincipalRequest());

        when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteBase));
        when(clienteRepository.save(any(Cliente.class))).thenReturn(clienteBase);
        when(direccionRepository.findByClienteIdCliente(1L)).thenReturn(List.of());

        ClienteResponse resultado = clienteService.actualizar(1L, request);

        assertThat(clienteBase.getNombre()).isEqualTo("Pedro");
        assertThat(clienteBase.getCorreo()).isEqualTo("pedro@test.com");
        verify(clienteRepository).save(clienteBase);
    }

    // ── eliminar ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("eliminar() borra el cliente si existe")
    void eliminar_borraCliente() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteBase));
        doNothing().when(clienteRepository).deleteById(1L);

        assertThatCode(() -> clienteService.eliminar(1L)).doesNotThrowAnyException();
        verify(clienteRepository).deleteById(1L);
    }

    @Test
    @DisplayName("eliminar() lanza 404 si cliente no existe")
    void eliminar_noExiste_lanza404() {
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.eliminar(99L))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── listarDirecciones ──────────────────────────────────────────────────

    @Test
    @DisplayName("listarDirecciones() retorna direcciones del cliente")
    void listarDirecciones_retornaDirecciones() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteBase));
        when(direccionRepository.findByClienteIdCliente(1L)).thenReturn(List.of(direccionBase));

        List<DireccionClienteResponse> resultado = clienteService.listarDirecciones(1L);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getCalle()).isEqualTo("Av. Libertad");
    }

    // ── obtenerDireccionPrincipal ──────────────────────────────────────────

    @Test
    @DisplayName("obtenerDireccionPrincipal() retorna dirección principal")
    void obtenerDireccionPrincipal_retornaDireccion() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteBase));
        when(direccionRepository.findByClienteIdClienteAndEsPrincipalTrue(1L))
                .thenReturn(Optional.of(direccionBase));

        DireccionClienteResponse resultado = clienteService.obtenerDireccionPrincipal(1L);

        assertThat(resultado.getEsPrincipal()).isTrue();
        assertThat(resultado.getCalle()).isEqualTo("Av. Libertad");
    }

    @Test
    @DisplayName("obtenerDireccionPrincipal() lanza 404 si no tiene dirección principal")
    void obtenerDireccionPrincipal_sinDireccion_lanza404() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteBase));
        when(direccionRepository.findByClienteIdClienteAndEsPrincipalTrue(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.obtenerDireccionPrincipal(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no tiene dirección principal");
    }

    // ── eliminarDireccion ──────────────────────────────────────────────────

    @Test
    @DisplayName("eliminarDireccion() lanza 400 al intentar borrar la dirección principal")
    void eliminarDireccion_esPrincipal_lanza400() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteBase));
        when(direccionRepository.findById(1L)).thenReturn(Optional.of(direccionBase));

        assertThatThrownBy(() -> clienteService.eliminarDireccion(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No se puede eliminar la dirección principal");
    }

    @Test
    @DisplayName("eliminarDireccion() lanza FORBIDDEN si dirección no pertenece al cliente")
    void eliminarDireccion_noPertenece_lanzaForbidden() {
        Cliente otroCliente = Cliente.builder().idCliente(99L).build();
        DireccionCliente otraDireccion = DireccionCliente.builder()
                .idDireccion(5L)
                .cliente(otroCliente)
                .esPrincipal(false)
                .build();

        when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteBase));
        when(direccionRepository.findById(5L)).thenReturn(Optional.of(otraDireccion));

        assertThatThrownBy(() -> clienteService.eliminarDireccion(1L, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no pertenece al cliente");
    }
}
