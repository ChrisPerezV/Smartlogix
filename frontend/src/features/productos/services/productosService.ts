// frontend/src/features/productos/services/productosService.ts
import { apiGet, apiPost, apiPut } from "@/lib/api/client";
import { endpoints } from "@/lib/api/endpoints";
import { Producto } from "../types/producto";

interface PresentacionResponse {
    idPresentacion: number;
    sku: string;
    volumenMl: number;
    tipoEnvase: string;
    precioActual: number;
    pesoGramos: number;
    activo: boolean;
    imagenUrl?: string;
}

interface PerfumeResponse {
    idPerfume: number;
    nombre: string;
    descripcion: string;
    estado: string;
    marca?: { idMarca: number; nombre: string; paisOrigen?: string };
    familiaOlfativa?: { idFamilia: number; nombre: string; descripcion?: string };
    presentaciones: PresentacionResponse[];
}

function aplanarPerfume(perfume: PerfumeResponse): Producto[] {
    return (perfume.presentaciones ?? [])
        .filter((p) => p.activo && (perfume.estado ?? "").toUpperCase() === "ACTIVO")
        .map((p) => ({
            idProducto: p.idPresentacion,
            idPerfume: perfume.idPerfume,
            sku: p.sku,
            nombre: `${perfume.nombre} ${p.volumenMl}ml`,
            descripcion: perfume.descripcion,
            precioActual: p.precioActual,
            pesoGramos: p.pesoGramos,
            dimensiones: p.tipoEnvase,
            estado: perfume.estado,
            stockTotal: 0,
            imagenUrl: p.imagenUrl,
            volumenMl: p.volumenMl,
            marca: perfume.marca,
            familiaOlfativa: perfume.familiaOlfativa,
        }));
}

export const productosService = {
    getAll: async (): Promise<Producto[]> => {
        const rutaPerfumes = endpoints.perfumes || "/api/perfumes";
        const perfumes = await apiGet<PerfumeResponse[]>(rutaPerfumes);
        const productosAplanados = perfumes.flatMap(aplanarPerfume);

        const productosConStock = await Promise.all(
            productosAplanados.map(async (producto) => {
                try {
                    const inventarioUrl = rutaPerfumes.replace(
                        "/perfumes",
                        `/inventario/presentacion/${producto.idProducto}`
                    );
                    const inventarios = await apiGet<any[]>(inventarioUrl);
                    const stockReal = inventarios.reduce(
                        (sum: number, inv: any) => sum + (inv.stockDisponible || 0),
                        0
                    );
                    return { ...producto, stockTotal: stockReal };
                } catch {
                    return { ...producto, stockTotal: 0 };
                }
            })
        );
        return productosConStock;
    },

    getById: async (id: number) => {
        const productos = await productosService.getAll();
        return productos.find((item) => item.idProducto === id) ?? null;
    },

    // ✅ FIX PRINCIPAL: usa apiPost/apiPut del cliente centralizado
    // que ya apunta a NEXT_PUBLIC_API_URL con el token Bearer incluido
    create: async (productoData: any): Promise<void> => {
        // 1. Crear Perfume
        const perfumeCreado = await apiPost<{ idPerfume: number }>("/api/perfumes", {
            idMarca: productoData.marcaId ? Number(productoData.marcaId) : 1,
            idFamilia: productoData.familiaId ? Number(productoData.familiaId) : null,
            nombre: productoData.nombre,
            descripcion: productoData.descripcion,
            estado: "ACTIVO",
        });

        // 2. Crear Presentación
        await apiPost("/api/presentaciones", {
            idPerfume: perfumeCreado.idPerfume,
            sku: productoData.sku,
            volumenMl: Number(productoData.volumenMl) || 100,
            tipoEnvase: productoData.dimensiones || "spray",
            precioActual: Number(productoData.precioActual),
            pesoGramos: Number(productoData.pesoGramos) || 0,
            imagenUrl: productoData.imagenUrl || null,
            activo: true,
        });
    },

    update: async (idPresentacion: number, productoData: any): Promise<void> => {
        // Validación explícita antes de llamar
        if (!productoData.idPerfume) {
            throw new Error("No se encontró el idPerfume del producto. Recarga la página e intenta de nuevo.");
        }

        // 1. Actualizar Perfume base
        await apiPut(`/api/perfumes/${productoData.idPerfume}`, {
            idMarca: productoData.marcaId
                ? Number(productoData.marcaId)
                : productoData.marca?.idMarca ?? 1,
            idFamilia:
                productoData.familiaId
                    ? Number(productoData.familiaId)
                    : productoData.familiaOlfativa?.idFamilia ?? null,
            nombre: productoData.nombre,
            descripcion: productoData.descripcion,
            estado: (productoData.estado || "ACTIVO").toUpperCase(),
        });

        // 2. Actualizar Presentación
        await apiPut(`/api/presentaciones/${idPresentacion}`, {
            idPerfume: productoData.idPerfume,
            sku: productoData.sku,                              // ✅ siempre presente desde el estado del form
            volumenMl: Number(productoData.volumenMl) || 100,
            tipoEnvase: productoData.dimensiones || "spray",
            precioActual: Number(productoData.precioActual),   // ✅ Number() evita que llegue como string
            pesoGramos: Number(productoData.pesoGramos) || 0,
            imagenUrl: productoData.imagenUrl || null,
            activo: (productoData.estado || "ACTIVO").toUpperCase() === "ACTIVO",
        });
    },

    remove: async (idPresentacion: number): Promise<void> => {
        const current = await productosService.getById(idPresentacion);
        if (!current) throw new Error("Producto no encontrado para eliminar.");
        await productosService.update(idPresentacion, { ...current, estado: "INACTIVO" });
    },
};