# 🎤 Guion de Jason — Seguridad / JWT + Cierre del frontend

> **Tu parte de la presentación de Krypton.** Hablás en **5 slides**, en **dos momentos**: la seguridad (slides 9–11) y después el tramo final de frontend y calidad (slides 20–21).
>
> **Cómo usar esto:** el texto entre **«comillas»** es para LEER en voz alta. Mejor todavía: aprendételo y decilo natural. Lo de _"En pantalla"_ es lo que se ve en la diapo (pocas palabras); el detalle lo ponés vos al hablar.
>
> **Consejo:** en el slide 10, si podés mostrar un token real decodificado en pantalla, IMPACTA. Tenelo listo.

**Tus turnos:** Slides **9, 10, 11** (JWT) y **20, 21** (frontend + calidad). **Duración:** ~4–5 min.

---

## Slide 9 — Login: el flujo
**En pantalla:** Front → `POST /api/auth/login` → users verifica password (BCrypt) → **emite el JWT** → el front lo guarda → lo manda en cada request.

**Decís:**

«Gracias, Tayra. Ahora les toca la seguridad, que es transversal a todos los servicios. Todo empieza en el login: el usuario manda su email y su contraseña al users-service, con un POST a /api/auth/login. Ese servicio verifica la contraseña —que está guardada encriptada con BCrypt— y, si está todo bien, emite un JWT. Ese token es el pasaporte del usuario. El frontend lo guarda y, de ahí en adelante, lo manda en cada pedido que hace.»

---

## Slide 10 — ¿Qué es un JWT?
**En pantalla:** `header.payload.signature` · header = algoritmo · payload = email + rol + expiración · signature = firma · **firmado, NO encriptado**.

**Decís:**

«¿Y qué es ese token? Un JWT tiene tres partes separadas por puntos: header, payload y signature. El header dice con qué algoritmo se firmó. El payload lleva los datos: el email, el rol y cuándo expira. Y la firma es lo que garantiza que nadie lo tocó. Algo clave que se confunde mucho: el token está FIRMADO, no encriptado. Cualquiera lo puede leer —de hecho lo podés pegar en una página y ver el contenido— pero nadie lo puede modificar sin el secreto, porque la firma no le cerraría.»

---

## Slide 11 — JWT stateless + secreto compartido
**En pantalla:** `users` FIRMA con el secreto · los demás VERIFICAN con el MISMO secreto · sin sesión, sin DB, sin preguntarle a users · el secreto compartido = el "contrato".

**Decís:**

«Y acá está la magia de por qué esto escala. El users-service firma el token con un secreto. Todos los demás servicios verifican ese token con el MISMO secreto. Eso significa que cada servicio valida por su cuenta, sin sesión, sin base de datos y sin tener que preguntarle a users si el token es válido. El secreto compartido es el contrato entre todos: mismo secreto, misma firma, confianza. Y ojo, el secreto nunca viaja dentro del token.»

👉 **Traspaso:** «Con la seguridad clara, le paso a Curi que les cuenta el catálogo y las reseñas.»

---
### ⏸️ Pausa — ahora hablan otros
Acá hablan **Curi** (12–13), **Cristofer** (14–16) y **Sergio** (17–19). **Tu próximo turno es el Slide 20.** Sergio cierra el slide 19 diciendo *"le paso a Jason para el tramo final"* — esa es tu entrada.

---

## Slide 20 — Frontend conectado
**En pantalla:** React 19 + Vite · todo pega al gateway (:8080) · lo NUEVO: reseñas en el producto · cupón en el checkout · pago de la orden · panel admin de cupones.

**Decís:**

«Vuelvo yo para el tramo final. Una palabra del frontend: está hecho en React 19 con Vite, y todo lo que hace pega contra el gateway, el mismo 8080 de siempre. No cambió de puerta. Lo que le sumamos fueron las pantallas de los servicios nuevos: las reseñas en la página del producto, el cupón en el checkout, el pago de la orden y un panel de administración para los cupones.»

---

## Slide 21 — Calidad y verificación
**En pantalla:** TDD donde aporta (saga, stock, pagos, cupón — tests verdes) · verificación con **curls reales** + chequeo en la DB · todo containerizado.

**Decís:**

«Y algo de lo que estamos orgullosos: no confiamos en el "debería andar". Cada fase la verificamos con requests reales y mirando la base de datos para confirmar que los datos quedaban bien. Y los conceptos más críticos —como la saga del checkout, el stock y los pagos— los cubrimos con tests automatizados, todos en verde. Además, todo corre containerizado.»

👉 **Traspaso:** «Para verlo funcionando de verdad, le paso a Cristofer que va a hacer la demo en vivo.»

---

> ✅ Listo, terminaste. Quedate cerca para la demo y las preguntas finales.
