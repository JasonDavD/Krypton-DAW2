# 🎤 Guion de Tayra — Apertura + Infraestructura

> **Tu parte de la presentación de Krypton.** Hablás en **5 slides**, en **dos momentos**: abrís la charla (slides 1–2) y después volvés con la infraestructura (slides 6–8).
>
> **Cómo usar esto:** el texto entre **«comillas»** es para LEER en voz alta. Mejor todavía: aprendételo y decilo natural. Lo de _"En pantalla"_ es lo que se ve en la diapo (pocas palabras); el detalle lo ponés vos al hablar.
>
> **Consejo:** no leas como robot. Practicá 2–3 veces y vas a poder mirar al público.

**Tus turnos:** Slides **1, 2** (apertura) y **6, 7, 8** (infra). **Duración:** ~4–5 min.

---

## Slide 1 — Portada
**En pantalla:** Logo Krypton · "Migración de un Monolito a Microservicios" · CIBERTEC – DAW II / EFSRT VI · Integrantes · Fecha.

**Decís:**

«Buenas a todos, somos el equipo de Krypton. Yo soy Tayra y, junto con Jason, Curi, Cristofer y Sergio, les vamos a mostrar cómo tomamos un e-commerce que funcionaba como un monolito y lo convertimos en nueve microservicios. Es nuestro proyecto de CIBERTEC, para Desarrollo de Aplicaciones Web II.»

---

## Slide 2 — Agenda
**En pantalla:** 1) Qué es Krypton · 2) Por qué microservicios · 3) La arquitectura · 4) Cada servicio y patrón · 5) Demo · 6) Conclusiones.

**Decís:**

«En los próximos minutos vamos a recorrer seis cosas: primero, qué es Krypton; segundo, por qué decidimos pasar a microservicios; tercero, cómo quedó la arquitectura; cuarto, vamos a entrar en cada servicio y cada patrón que usamos; quinto, una demo en vivo; y para cerrar, las conclusiones.»

👉 **Traspaso:** «Para arrancar, le dejo la palabra a Curi, que les va a contar de qué se trata Krypton.»

---
### ⏸️ Pausa — ahora hablan otros
Acá hablan **Curi** (slides 3–5). **Tu próximo turno es el Slide 6.** Quedate atenta: Curi cierra el slide 5 diciendo *"arranca Tayra con la infraestructura"* — esa es tu entrada.

---

## Slide 6 — Service Discovery (Eureka)
**En pantalla:** Las IPs/puertos cambian, no se hardcodean · un **registro** vivo · cada servicio se anota al arrancar y los demás lo buscan por nombre.

**Decís:**

«Gracias, Curi. Yo me voy a meter con la base de todo esto: cómo hacen los servicios para encontrarse entre ellos. Piensen en una guía telefónica: en vez de memorizarse el número de cada persona, buscás por el nombre y la guía te da el número. Bueno, Eureka es exactamente eso. Tenemos un servicio, el eureka-server, que es un registro vivo: cada microservicio, cuando arranca, se anota ahí con su nombre. Y cuando uno necesita hablar con otro, no usa una IP fija —que además cambia— sino que lo busca por nombre en ese registro.»

---

## Slide 7 — API Gateway
**En pantalla:** Una sola puerta (`:8080`) · rutea por nombre (`lb://users-service`) resolviendo con Eureka · centraliza CORS · el front no conoce los puertos internos.

**Decís:**

«Ahora, si tenemos nueve servicios, ¿el frontend tiene que conocerse los nueve puertos? No. Para eso está el API Gateway: es una sola puerta de entrada, el puerto 8080, que es a donde apunta el frontend. El navegador habla solo con el gateway, y el gateway reparte cada pedido al servicio que corresponde. Y lo resuelve por nombre, apoyándose en Eureka. Cuando vean una ruta tipo "lb://users-service", ese "lb" significa load balancer: resolvé por Eureka y balanceá. El front nunca se entera de los puertos internos.»

---

## Slide 8 — Dockerización
**En pantalla:** Un `Dockerfile` por servicio · `docker-compose.full.yml` levanta TODO con un comando · parametrización por env · 11 contenedores.

**Decís:**

«Y para que todo esto se levante fácil, empaquetamos cada servicio en una imagen de Docker, con su propio Dockerfile. Después, con un solo comando, docker compose up, levantamos todo junto: la base de datos MySQL, RabbitMQ, los nueve servicios y el gateway. Once contenedores con una sola línea. El truco para que funcione es que las direcciones son configurables: en mi máquina los servicios se hablan por localhost, pero dentro de Docker se hablan por el nombre del contenedor.»

👉 **Traspaso:** «Con la infraestructura lista, ahora Jason les va a contar cómo aseguramos todo esto: el tema de la seguridad y los tokens.»

---

> ✅ Listo, terminaste. De acá en más podés relajarte hasta la demo y las preguntas finales.
