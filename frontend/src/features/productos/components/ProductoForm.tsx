"use client";

import { useState, ChangeEvent, FormEvent, useEffect } from "react";
import Image from "next/image";
import { Producto } from "@/features/productos/types/producto";
import { productosService } from "@/features/productos/services/productosService";

// Función mágica que convierte a WebP en el navegador
const convertToWebP = (file: File): Promise<File> => {
    return new Promise((resolve, reject) => {
        const img = new window.Image();
        img.src = URL.createObjectURL(file);
        img.onload = () => {
            const canvas = document.createElement("canvas");
            canvas.width = img.width;
            canvas.height = img.height;
            const ctx = canvas.getContext("2d");
            if (!ctx) return reject(new Error("No se pudo crear el contexto 2d"));
            ctx.drawImage(img, 0, 0);
            canvas.toBlob((blob) => {
                if (!blob) return reject(new Error("Error al convertir a WebP"));
                const webpFile = new File([blob], "imagen.webp", { type: "image/webp" });
                resolve(webpFile);
            }, "image/webp", 0.8);
        };
        img.onerror = () => reject(new Error("Error al cargar la imagen para conversión"));
    });
};

interface ProductoFormProps {
    onSuccess?: () => void;
    initialData?: Producto | null; // Recibe datos si estamos editando
}

export function ProductoForm({ onSuccess, initialData }: ProductoFormProps) {
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const [imageFile, setImageFile] = useState<File | null>(null);
    const [imagePreview, setImagePreview] = useState<string | null>(null);

    const [formData, setFormData] = useState({
        nombre: "",
        sku: "",
        descripcion: "",
        precioActual: "",
        volumenMl: "",
        pesoGramos: "",
        tipoEnvase: "spray",
        marcaId: "",
        familiaId: "",
    });

    // Rellenamos el formulario si viene data inicial (Modo Edición)
    useEffect(() => {
        if (initialData) {
            setFormData({
                nombre: initialData.nombre || "",
                sku: initialData.sku || "",
                descripcion: initialData.descripcion || "",
                precioActual: initialData.precioActual?.toString() || "",
                volumenMl: initialData.volumenMl?.toString() || "",
                pesoGramos: initialData.pesoGramos?.toString() || "",
                tipoEnvase: initialData.dimensiones || "spray",
                marcaId: initialData.marca?.idMarca?.toString() || "",
                familiaId: initialData.familiaOlfativa?.idFamilia?.toString() || "",
            });
            setImagePreview(initialData.imagenUrl || null);
        }
    }, [initialData]);

    const handleChange = (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleImageChange = (e: ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (file) {
            setImageFile(file);
            setImagePreview(URL.createObjectURL(file));
        }
    };

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();

        if (!formData.sku) {
            setError("Debes ingresar un SKU antes de guardar (se usará como nombre de la imagen).");
            return;
        }

        setIsSubmitting(true);
        setError(null);

        try {
            // Si estamos editando y no subimos foto nueva, mantenemos la anterior
            let uploadedImageUrl = initialData?.imagenUrl || "";

            // 1. SUBIDA DE IMAGEN A AWS (Si seleccionó una nueva)
            if (imageFile) {
                const webpFile = await convertToWebP(imageFile);
                const uploadData = new FormData();
                uploadData.append("archivo", webpFile);
                uploadData.append("sku", formData.sku);

                const uploadRes = await fetch("/api/archivos/subir", {
                    method: "POST",
                    body: uploadData
                });

                if (!uploadRes.ok) {
                    throw new Error("Error al subir la imagen al servidor AWS.");
                }
                uploadedImageUrl = await uploadRes.text();
            }

            // 2. CREACIÓN O EDICIÓN DEL PRODUCTO
            const payload = {
            nombre: formData.nombre,
            sku: formData.sku,          // ✅ viene del estado del form, no del input deshabilitado
            descripcion: formData.descripcion,
            precioActual: Number(formData.precioActual),
            volumenMl: Number(formData.volumenMl),
            pesoGramos: Number(formData.pesoGramos),
            dimensiones: formData.tipoEnvase,
            estado: initialData?.estado || "ACTIVO",
            imagenUrl: uploadedImageUrl,
            // ✅ CRÍTICO: estos campos deben pasar para que update() funcione
            idPerfume: initialData?.idPerfume,
            marcaId: formData.marcaId || initialData?.marca?.idMarca?.toString() || "",
            familiaId: formData.familiaId || initialData?.familiaOlfativa?.idFamilia?.toString() || "",
            marca: initialData?.marca,
            familiaOlfativa: initialData?.familiaOlfativa,
        };

            if (initialData?.idProducto) {
                // Actualizar
                await productosService.update(initialData.idProducto, {
                    ...payload,
                    idProducto: initialData.idProducto
                });
                alert("Producto actualizado exitosamente.");
            } else {
                // Crear
                await productosService.create(payload);
                alert("Producto creado exitosamente.");
            }

            if (onSuccess) onSuccess();

        } catch (err) {
            console.error(err);
            setError(err instanceof Error ? err.message : "Hubo un error al guardar el producto.");
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <form onSubmit={handleSubmit} className="max-w-4xl mx-auto bg-white border border-gray-200 p-6 md:p-10 text-black">
            <div className="mb-8 border-b border-gray-100 pb-6">
                <h2 className="text-2xl font-bold uppercase tracking-tighter text-black">
                    {initialData ? "Editar Perfume" : "Crear Nuevo Perfume"}
                </h2>
                <p className="text-xs uppercase tracking-widest text-gray-500 mt-2 font-semibold">
                    {initialData ? "Modifica los datos del producto." : "Ingresa los datos y sube la imagen. Se convertirá a WebP automáticamente."}
                </p>
            </div>

            {error && (
                <div className="mb-6 border border-red-200 bg-red-50 p-4 text-xs uppercase tracking-widest font-bold text-red-600">
                    {error}
                </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                <div className="flex flex-col space-y-5">
                    <div>
                        <label className="block text-[10px] uppercase tracking-widest font-bold text-gray-500 mb-2">Nombre del Perfume</label>
                        <input required type="text" name="nombre" value={formData.nombre} onChange={handleChange} className="w-full border border-gray-300 p-3 text-sm rounded-none focus:outline-none focus:border-black transition-colors" placeholder="Ej: Sauvage EDP" />
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-[10px] uppercase tracking-widest font-bold text-gray-500 mb-2">SKU</label>
                            <input required type="text" name="sku" value={formData.sku} onChange={handleChange} disabled={!!initialData} className="w-full border border-gray-300 p-3 text-sm rounded-none focus:outline-none focus:border-black transition-colors uppercase disabled:bg-gray-100 disabled:text-gray-400" placeholder="Ej: DIO-SAU-100" />
                        </div>
                        <div>
                            <label className="block text-[10px] uppercase tracking-widest font-bold text-gray-500 mb-2">Precio ($)</label>
                            <input required type="number" name="precioActual" value={formData.precioActual} onChange={handleChange} className="w-full border border-gray-300 p-3 text-sm rounded-none focus:outline-none focus:border-black transition-colors" placeholder="Ej: 89990" />
                        </div>
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-[10px] uppercase tracking-widest font-bold text-gray-500 mb-2">Volumen (ML)</label>
                            <input required type="number" name="volumenMl" value={formData.volumenMl} onChange={handleChange} className="w-full border border-gray-300 p-3 text-sm rounded-none focus:outline-none focus:border-black transition-colors" placeholder="Ej: 100" />
                        </div>
                        <div>
                            <label className="block text-[10px] uppercase tracking-widest font-bold text-gray-500 mb-2">Peso (Gramos)</label>
                            <input required type="number" name="pesoGramos" value={formData.pesoGramos} onChange={handleChange} className="w-full border border-gray-300 p-3 text-sm rounded-none focus:outline-none focus:border-black transition-colors" placeholder="Ej: 250" />
                        </div>
                    </div>

                    <div>
                        <label className="block text-[10px] uppercase tracking-widest font-bold text-gray-500 mb-2">Descripción</label>
                        <textarea required name="descripcion" value={formData.descripcion} onChange={handleChange} rows={4} className="w-full border border-gray-300 p-3 text-sm rounded-none focus:outline-none focus:border-black transition-colors resize-none" placeholder="Aromático y especiado..." />
                    </div>
                </div>

                <div className="flex flex-col space-y-5">
                    <div>
                        <label className="block text-[10px] uppercase tracking-widest font-bold text-gray-500 mb-2">Imagen de Presentación</label>

                        <div className="relative border-2 border-dashed border-gray-300 bg-gray-50 hover:bg-gray-100 transition-colors flex flex-col items-center justify-center aspect-[4/5] w-full cursor-pointer overflow-hidden group">
                            {imagePreview ? (
                                <>
                                    <Image src={imagePreview} alt="Vista previa" fill className="object-cover" />
                                    <div className="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                                        <span className="text-white text-xs uppercase tracking-widest font-bold">Cambiar Imagen</span>
                                    </div>
                                </>
                            ) : (
                                <div className="text-center p-6">
                                    <svg className="mx-auto h-8 w-8 text-gray-400 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="square" strokeLinejoin="miter" strokeWidth="1" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                    </svg>
                                    <p className="text-xs uppercase tracking-widest text-gray-500 font-semibold">Haz clic para subir foto</p>
                                </div>
                            )}

                            <input
                                type="file"
                                accept="image/png, image/jpeg, image/webp"
                                onChange={handleImageChange}
                                className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
                            />
                        </div>
                    </div>

                    <div>
                        <label className="block text-[10px] uppercase tracking-widest font-bold text-gray-500 mb-2">Tipo de Envase</label>
                        <select name="tipoEnvase" value={formData.tipoEnvase} onChange={handleChange} className="w-full border border-gray-300 p-3 text-sm rounded-none focus:outline-none focus:border-black transition-colors bg-white">
                            <option value="spray">Spray</option>
                            <option value="splash">Splash</option>
                            <option value="roll-on">Roll-on</option>
                        </select>
                    </div>
                </div>
            </div>

            <div className="mt-10 pt-6 border-t border-gray-100 flex gap-4">
                <button
                    type="button"
                    onClick={() => { if(onSuccess) onSuccess() }}
                    className="w-1/3 py-4 text-sm uppercase tracking-widest font-bold transition-all rounded-none border border-gray-300 bg-white text-black hover:bg-gray-50"
                >
                    Cancelar
                </button>
                <button
                    type="submit"
                    disabled={isSubmitting}
                    className="w-2/3 py-4 text-sm uppercase tracking-widest font-bold transition-all rounded-none border-2 border-black bg-black text-white hover:bg-white hover:text-black disabled:opacity-50 disabled:cursor-not-allowed"
                >
                    {isSubmitting ? "Guardando..." : initialData ? "Actualizar Producto" : "Crear Producto"}
                </button>
            </div>
        </form>
    );
}