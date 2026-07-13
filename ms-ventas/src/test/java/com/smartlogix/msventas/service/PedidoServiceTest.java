package com.smartlogix.msventas.service;

import com.smartlogix.msventas.client.ClienteClient;
import com.smartlogix.msventas.client.InventarioClient;
import com.smartlogix.msventas.client.LogisticaClient;
import com.smartlogix.msventas.dto.DetallePedidoRequest;
import com.smartlogix.msventas.dto.DireccionResponse;
import com.smartlogix.msventas.dto.PedidoRequest;
import com.smartlogix.msventas.dto.PresentacionResponse;
import com.smartlogix.msventas.model.DetallePedido;
import com.smartlogix.msventas.model.Pedido;
import com.smartlogix.msventas.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PedidoService - Tests unitarios")
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private InventarioClient inventarioClient;
    @Mock
    private ClienteClient clienteClient;
    @Mock
    private LogisticaClient logisticaClient;

    @InjectMocks
    private PedidoService pedidoService;

    private Pedido pedidoBase;
    private DetallePedido detalleBase;

    @BeforeEach
    void setUp() {
        detalleBase = new DetallePedido();
        detalleBase.setIdPresentacion(1L);
        detalleBase.setCantidad(2);
        detalleBase.setPrecioUnitarioSnapshot(5000);

        pedidoBase = new Pedido();
        pedidoBase.setIdPedido(1L);
        pedidoBase.setIdCliente(10L);
        pedidoBase.setEstadoPedido("PENDIENTE_PAGO");
        pedidoBase.setMontoTotal(10000L);
        pedidoBase.setFechaCreacion(OffsetDateTime.now());
        List<DetallePedido> detalles = new ArrayList<>();
        detalles.add(detalleBase);
        pedidoBase.setDetalles(detalles);
        detalleBase.setPedido(pedidoBase);
    }

    // ── listar ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listar() retorna lista de pedidos")
    void listar_retornaListaPedidos() {
        when(pedidoRepository.findAll()).thenReturn(List.of(pedidoBase));

        List<Pedido> resultado = pedidoService.listar();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getIdPedido()).isEqualTo(1L);
        verify(pedidoRepository).findAll();
    }

    @Test
    @DisplayName("listar() retorna lista vacía cuando no hay pedidos")
    void listar_retornaListaVacia() {
        when(pedidoRepository.findAll()).thenReturn(List.of());

        List<Pedido> resultado = pedidoService.listar();

        assertThat(resultado).isEmpty();
    }

    // ── buscarPorId ────────────────────────────────────────────────────────

    @Test
    @DisplayName("buscarPorId() retorna pedido existente")
    void buscarPorId_retornaPedido() {
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoBase));

        Pedido resultado = pedidoService.buscarPorId(1L);

        assertThat(resultado.getIdPedido()).isEqualTo(1L);
        assertThat(resultado.getEstadoPedido()).isEqualTo("PENDIENTE_PAGO");
    }

    @Test
    @DisplayName("buscarPorId() lanza 404 cuando no existe")
    void buscarPorId_noExiste_lanza404() {
        when(pedidoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pedidoService.buscarPorId(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no encontrado");
    }

    // ── crear ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("crear() guarda pedido con monto total calculado correctamente")
    void crear_calculaMontoYGuarda() {
        PresentacionResponse presentacion = new PresentacionResponse(1L, new BigDecimal("5000"), "Perfume X", 100);
        DetallePedidoRequest detalleReq = new DetallePedidoRequest(1L, 2);
        PedidoRequest request = new PedidoRequest(10L, List.of(detalleReq));

        doNothing().when(clienteClient).validarCliente(10L);
        when(inventarioClient.obtenerPresentacion(1L)).thenReturn(presentacion);
        doNothing().when(inventarioClient).reservarStock(1L, 2);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            p.setIdPedido(1L);
            return p;
        });

        Pedido resultado = pedidoService.crear(request);

        assertThat(resultado.getMontoTotal()).isEqualTo(10000L);
        assertThat(resultado.getEstadoPedido()).isEqualTo("PENDIENTE_PAGO");
        assertThat(resultado.getIdCliente()).isEqualTo(10L);
        verify(inventarioClient).reservarStock(1L, 2);
        verify(pedidoRepository).save(any(Pedido.class));
    }

    @Test
    @DisplayName("crear() lanza 400 cuando cliente no válido")
    void crear_clienteInvalido_lanza400() {
        DetallePedidoRequest detalleReq = new DetallePedidoRequest(1L, 2);
        PedidoRequest request = new PedidoRequest(99L, List.of(detalleReq));

        doThrow(new RuntimeException("Cliente no encontrado"))
                .when(clienteClient).validarCliente(99L);

        assertThatThrownBy(() -> pedidoService.crear(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cliente no válido");
    }

    // ── cambiarEstado ──────────────────────────────────────────────────────

    @Test
    @DisplayName("cambiarEstado() actualiza el estado del pedido")
    void cambiarEstado_actualizaEstado() {
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoBase));
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedidoBase);

        Pedido resultado = pedidoService.cambiarEstado(1L, "PAGADO");

        assertThat(resultado.getEstadoPedido()).isEqualTo("PAGADO");
        verify(pedidoRepository).save(pedidoBase);
    }

    // ── listarPorCliente ───────────────────────────────────────────────────

    @Test
    @DisplayName("listarPorCliente() retorna pedidos del cliente")
    void listarPorCliente_retornaPedidosDelCliente() {
        when(pedidoRepository.findByIdCliente(10L)).thenReturn(List.of(pedidoBase));

        List<Pedido> resultado = pedidoService.listarPorCliente(10L);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getIdCliente()).isEqualTo(10L);
    }

    // ── cancelarPedido ─────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelarPedido() cambia estado a CANCELADO y libera stock")
    void cancelarPedido_liberaStockYCancela() {
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoBase));
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedidoBase);
        doNothing().when(inventarioClient).liberarStock(anyLong(), anyInt());

        Pedido resultado = pedidoService.cancelarPedido(1L);

        assertThat(resultado.getEstadoPedido()).isEqualTo("CANCELADO");
        verify(inventarioClient).liberarStock(1L, 2);
    }

    @Test
    @DisplayName("cancelarPedido() lanza 400 si pedido no está en PENDIENTE_PAGO")
    void cancelarPedido_estadoInvalido_lanza400() {
        pedidoBase.setEstadoPedido("PAGADO");
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoBase));

        assertThatThrownBy(() -> pedidoService.cancelarPedido(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PENDIENTE_PAGO");
    }

    // ── cancelarPedidoYLiberarStock ────────────────────────────────────────

    @Test
    @DisplayName("cancelarPedidoYLiberarStock() cancela y libera stock cuando está PENDIENTE_PAGO")
    void cancelarPedidoYLiberarStock_ok() {
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoBase));
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedidoBase);
        doNothing().when(inventarioClient).liberarStock(anyLong(), anyInt());

        pedidoService.cancelarPedidoYLiberarStock(1L);

        assertThat(pedidoBase.getEstadoPedido()).isEqualTo("CANCELADO");
        verify(inventarioClient).liberarStock(1L, 2);
    }

    @Test
    @DisplayName("cancelarPedidoYLiberarStock() no hace nada si no está PENDIENTE_PAGO")
    void cancelarPedidoYLiberarStock_ignoraEstadoDistinto() {
        pedidoBase.setEstadoPedido("PAGADO");
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoBase));

        pedidoService.cancelarPedidoYLiberarStock(1L);

        verify(inventarioClient, never()).liberarStock(anyLong(), anyInt());
        verify(pedidoRepository, never()).save(any());
    }

    // ── procesarPagoExitoso ────────────────────────────────────────────────

    @Test
    @DisplayName("procesarPagoExitoso() cambia estado a PAGADO y crea despacho")
    void procesarPagoExitoso_ok() {
        DireccionResponse direccion = new DireccionResponse(1L, 10L, 1, "Av. Libertad", "123", null, true, "Santiago");
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoBase));
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedidoBase);
        when(clienteClient.obtenerDireccionPrincipal(10L)).thenReturn(direccion);
        doNothing().when(inventarioClient).descontarStock(anyLong(), anyInt(), anyString());
        doNothing().when(logisticaClient).crearDespacho(anyLong(), anyString(), anyString());

        pedidoService.procesarPagoExitoso(1L);

        assertThat(pedidoBase.getEstadoPedido()).isEqualTo("PAGADO");
        verify(logisticaClient).crearDespacho(eq(1L), anyString(), anyString());
        verify(inventarioClient).descontarStock(eq(1L), eq(2), anyString());
    }

    @Test
    @DisplayName("procesarPagoExitoso() lanza CONFLICT si pedido no está PENDIENTE_PAGO")
    void procesarPagoExitoso_noEsPendiente_lanzaConflict() {
        pedidoBase.setEstadoPedido("PAGADO");
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoBase));

        assertThatThrownBy(() -> pedidoService.procesarPagoExitoso(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no está pendiente de pago");
    }

    @Test
    @DisplayName("procesarPagoExitoso() continúa con dirección default si ms-clientes falla")
    void procesarPagoExitoso_clienteFalla_usaDireccionDefault() {
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoBase));
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedidoBase);
        when(clienteClient.obtenerDireccionPrincipal(10L))
                .thenThrow(new RuntimeException("ms-clientes no disponible"));
        doNothing().when(inventarioClient).descontarStock(anyLong(), anyInt(), anyString());
        doNothing().when(logisticaClient).crearDespacho(anyLong(), anyString(), anyString());

        assertThatCode(() -> pedidoService.procesarPagoExitoso(1L)).doesNotThrowAnyException();
        verify(logisticaClient).crearDespacho(anyLong(), anyString(), anyString());
    }
}
