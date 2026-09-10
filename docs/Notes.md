# ICAR API — Notes

Decisiones de diseño y contexto que no cabe directamente en los diagramas. Ver [ERD](./ERD.md) para el modelo de
datos.

## Donation
No tiene relación con `Member` a propósito: donar es una acción pública abierta a cualquiera que entre al sitio,
no requiere ser miembro. El donante solo escribe su nombre (`donor_name`) y opcionalmente un mensaje (`message`);
`payer_email` no es un campo del formulario — se completa solo cuando el método es PAYPAL, tomado de la respuesta
de captura de PayPal.

## Member.status
Cubre el ciclo de vida de un miembro de la congregación: visitante, nuevo, en proceso de adoctrinamiento, activo,
inactivo, o fallecido (`VISITOR | NEW | IN_DISCIPLESHIP | ACTIVE | INACTIVE | DECEASED`). Es deliberadamente un
módulo aparte de donaciones.

## Member ↔ Family
`Member.family_id` (a qué familia pertenece) y `Family.responsible_member_id` (quién es el responsable de esa
familia) son dos relaciones separadas hacia el mismo par de tablas — no es una referencia circular problemática,
solo hay que crear el `Member` responsable antes de (o al mismo tiempo que) asignarlo como responsable de su
`Family`.

## Member ↔ Ministry
Muchos-a-muchos (un miembro puede estar en 0, 1 o varios ministerios) — se implementa con una tabla intermedia
`member_ministry(member_id, ministry_id)`, aunque el diagrama la muestre como relación directa.

## Income
- `source_donation_id` es opcional — null si el ingreso fue registrado manualmente (no viene de una donación).
- `registered_by` es opcional — solo tiene valor cuando un usuario (ej. el tesorero) registra el ingreso a mano;
  cuando el ingreso se genera automáticamente porque una donación se completó, no lo registró ninguna persona,
  así que queda null.

**¿Por qué `Income` tiene `registered_by` si viene de una `Donation`?**
No siempre viene de una donación. `Income` representa *cualquier* ingreso de dinero a la iglesia, y hay dos formas
de que exista uno:
1. **Automático**: una `Donation` se completa (ej. el donante pagó por PayPal) → el sistema crea el `Income`
   solo, sin que nadie lo toque. Ahí `source_donation_id` apunta a esa donación y `registered_by` queda null
   (nadie lo "registró", lo generó el sistema).
2. **Manual**: el tesorero recibe dinero que no pasó por el flujo de donación del sitio (ej. alguien le da
   efectivo en mano, o hay un ingreso de otra fuente) y lo carga él mismo en el panel de admin. Ahí
   `source_donation_id` queda null y `registered_by` apunta al usuario que lo cargó — sirve para saber quién es
   responsable de ese registro si después hay que auditar o corregir algo.

## Budget
Es una meta/plan de cuánto dinero se espera ingresar o gastar en una categoría durante un periodo, definida *antes*
de que pase (ej. "Misiones: $2,000 planeados para el primer trimestre de 2026"). No es un movimiento de dinero
real — es solo un número de referencia. Con eso, el reporte "presupuesto vs. real" suma los `Income`/`Expense`
reales de esa categoría en ese periodo y los compara contra el `planned_amount` del `Budget`, para ver si van
gastando/recibiendo más, menos, o justo lo planeado.

## Imágenes
Las imágenes (`image_url` en `AboutUs`/`EventPhoto`) son URLs de S3, no binarios — el flujo de subida es aparte
(`POST /api/admin/files/upload`), y esta API solo persiste la URL resultante.

## Registros singleton
`AboutUs` y `ContactInfo` son registros "singleton" en la práctica (siempre existe 1 fila), aunque estén modelados
como tabla normal.
