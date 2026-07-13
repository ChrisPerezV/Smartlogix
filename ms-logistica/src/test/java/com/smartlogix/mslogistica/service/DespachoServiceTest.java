package com.smartlogix.mslogistica.service;

import com.smartlogix.mslogistica.dto.DespachoRequest;
import com.smartlogix.mslogistica.dto.DespachoResponse;
import com.smartlogix.mslogistica.dto.WebhookCourierRequest;
import com.smartlogix.mslogistica.exception.ResourceNotFoundException;
import com.smartlogix.mslogistica.model.Despacho;
import com.smartlogix.mslogistica.repository.DespachoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DespachoService - Tests unitarios")
class DespachoServiceTest {

    @Mock
    private DespachoRepository despachoRepository;

    @InjectMocks
    private DespachoService despachoService;

    private Despacho despachoBase;

    @BeforeEach
    void setUp() {
        despachoBase = Despacho.builder()
                .idDespacho(1L)
                .idPedido(10L)
                .direccionEntrega("Av. Libertad 123")
                .comunaEntrega("Santiago")
                .estadoDespacho("PENDIENTE")
                .fechaCreacion(OffsetDateTime.now())
                .fechaEntregaEstimada(OffsetDateTime.now().plusDays(3))
                .courier("STARKEN")
                .codigoSeguimiento("STK-ABCD1234")
                .urlSeguimiento("https://starken.cl/tracking?codigo=STK-ABCD1234")
                .build();
    }

    // ── listar ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listar() retorna lista de despachos")
    void listar_retornaDespachos() {
        when(despachoRepository.findAll()).thenReturn(List.of(despachoBase));

        List<DespachoResponse> resultado = despachoService.listar();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getIdDespacho()).isEqualTo(1L);
        assertThat(resultado.get(0).getCourier()).isEqualTo("STARKEN");
    }

    @Test
    @DisplayName("listar() retorna lista vacía cuando no hay despachos")
    void listar_retornaListaVacia() {
        when(despachoRepository.findAll()).thenReturn(List.of());

        List<DespachoResponse> resultado = despachoService.listar();

        assertThat(resultado).isEmpty();
    }

    // ── buscarPorId ────────────────────────────────────────────────────────

    @Test
    @DisplayName("buscarPorId() retorna despacho existente")
    void buscarPorId_retornaDespacho() {
        when(despachoRepository.findById(1L)).thenReturn(Optional.of(despachoBase));

        DespachoResponse resultado = despachoService.buscarPorId(1L);

        assertThat(resultado.getIdDespacho()).isEqualTo(1L);
        assertThat(resultado.getEstadoDespacho()).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("buscarPorId() lanza ResourceNotFoundException si no existe")
    void buscarPorId_noExiste_lanzaException() {
        when(despachoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> despachoService.buscarPorId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── buscarPorIdPedido ──────────────────────────────────────────────────

    @Test
    @DisplayName("buscarPorIdPedido() retorna despacho por pedido")
    void buscarPorIdPedido_retornaDespacho() {
        when(despachoRepository.findByIdPedido(10L)).thenReturn(Optional.of(despachoBase));

        DespachoResponse resultado = despachoService.buscarPorIdPedido(10L);

        assertThat(resultado.getIdPedido()).isEqualTo(10L);
    }

    @Test
    @DisplayName("buscarPorIdPedido() lanza ResourceNotFoundException si no hay despacho para el pedido")
    void buscarPorIdPedido_noExiste_lanzaException() {
        when(despachoRepository.findByIdPedido(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> despachoService.buscarPorIdPedido(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── crear ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("crear() genera código de seguimiento STARKEN y persiste el despacho")
    void crear_generaTrackingYPersiste() {
        DespachoRequest request = new DespachoRequest(10L, "Av. Libertad 123", "Santiago", null);

        when(despachoRepository.save(any(Despacho.class))).thenAnswer(inv -> {
            Despacho d = inv.getArgument(0);
            d.setIdDespacho(1L);
            return d;
        });

        DespachoResponse resultado = despachoService.crear(request);

        assertThat(resultado.getIdPedido()).isEqualTo(10L);
        assertThat(resultado.getCourier()).isEqualTo("STARKEN");
        assertThat(resultado.getCodigoSeguimiento()).startsWith("STK-");
        assertThat(resultado.getEstadoDespacho()).isEqualTo("PENDIENTE");
        assertThat(resultado.getFechaEntregaEstimada()).isAfter(OffsetDateTime.now());
        verify(despachoRepository).save(any(Despacho.class));
    }

    @Test
    @DisplayName("crear() respeta estadoDespacho del request si viene informado")
    void crear_respetaEstadoRequest() {
        DespachoRequest request = new DespachoRequest(10L, "Calle 1", "Valparaíso", "EN_RUTA");

        when(despachoRepository.save(any(Despacho.class))).thenAnswer(inv -> {
            Despacho d = inv.getArgument(0);
            d.setIdDespacho(2L);
            return d;
        });

        DespachoResponse resultado = despachoService.crear(request);

        assertThat(resultado.getEstadoDespacho()).isEqualTo("EN_RUTA");
    }

    // ── cambiarEstado ──────────────────────────────────────────────────────

    @Test
    @DisplayName("cambiarEstado() actualiza estado del despacho")
    void cambiarEstado_actualizaEstado() {
        when(despachoRepository.findById(1L)).thenReturn(Optional.of(despachoBase));
        when(despachoRepository.save(any(Despacho.class))).thenReturn(despachoBase);

        DespachoResponse resultado = despachoService.cambiarEstado(1L, "EN_RUTA");

        assertThat(resultado.getEstadoDespacho()).isEqualTo("EN_RUTA");
        verify(despachoRepository).save(despachoBase);
    }

    @Test
    @DisplayName("cambiarEstado() lanza ResourceNotFoundException si no existe el despacho")
    void cambiarEstado_noExiste_lanzaException() {
        when(despachoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> despachoService.cambiarEstado(99L, "ENTREGADO"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── procesarWebhook ────────────────────────────────────────────────────

    @Test
    @DisplayName("procesarWebhook() actualiza estado según código de seguimiento")
    void procesarWebhook_actualizaEstado() {
        WebhookCourierRequest request = new WebhookCourierRequest("STK-ABCD1234", "DELIVERED");
        when(despachoRepository.findByCodigoSeguimiento("STK-ABCD1234"))
                .thenReturn(Optional.of(despachoBase));
        when(despachoRepository.save(any(Despacho.class))).thenReturn(despachoBase);

        DespachoResponse resultado = despachoService.procesarWebhook(request);

        assertThat(resultado.getEstadoDespacho()).isEqualTo("ENTREGADO");
    }

    @Test
    @DisplayName("procesarWebhook() lanza ResourceNotFoundException si código no existe")
    void procesarWebhook_codigoNoExiste_lanzaException() {
        WebhookCourierRequest request = new WebhookCourierRequest("STK-INVALIDO", "DELIVERED");
        when(despachoRepository.findByCodigoSeguimiento("STK-INVALIDO"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> despachoService.procesarWebhook(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── mapearEstadoCourier (via procesarWebhook) ──────────────────────────

    @ParameterizedTest(name = "estadoCourier={0} → estadoInterno={1}")
    @CsvSource({
            "DELIVERED,  ENTREGADO",
            "ENTREGADO,  ENTREGADO",
            "IN_TRANSIT, EN_RUTA",
            "EN_RUTA,    EN_RUTA",
            "EXCEPTION,  PROBLEMA_ENTREGA",
            "EXTRAVIADO, PROBLEMA_ENTREGA",
            "DESCONOCIDO,PENDIENTE"
    })
    @DisplayName("mapearEstadoCourier() convierte correctamente el estado del courier")
    void mapearEstadoCourier_variosEstados(String estadoCourier, String estadoEsperado) {
        WebhookCourierRequest request = new WebhookCourierRequest("STK-ABCD1234", estadoCourier.trim());
        when(despachoRepository.findByCodigoSeguimiento("STK-ABCD1234"))
                .thenReturn(Optional.of(despachoBase));
        when(despachoRepository.save(any(Despacho.class))).thenAnswer(inv -> inv.getArgument(0));

        DespachoResponse resultado = despachoService.procesarWebhook(request);

        assertThat(resultado.getEstadoDespacho()).isEqualTo(estadoEsperado.trim());
    }
}
