import { Link } from 'react-router-dom';
import { LegalDoc } from './LegalDoc';

/** Términos y condiciones de uso de Krypton. */
export function TermsPage() {
  return (
    <LegalDoc title="Términos y condiciones" updated="19 de junio de 2026">
      <section>
        <h2>1. Aceptación</h2>
        <p>
          Al usar Krypton y al crear una cuenta aceptás estos Términos y condiciones. Si no estás
          de acuerdo con alguno de los puntos, te pedimos que no utilices la plataforma. Estos
          términos rigen la navegación, la creación de cuenta y la compra de productos en el sitio.
        </p>
      </section>

      <section>
        <h2>2. Cuenta de usuario</h2>
        <p>
          Para comprar necesitás una cuenta. Sos responsable de mantener la confidencialidad de tu
          contraseña y de toda la actividad que ocurra bajo tu cuenta. Los datos que registres deben
          ser veraces y estar actualizados.
        </p>
      </section>

      <section>
        <h2>3. Productos, precios e IGV</h2>
        <p>
          Los precios mostrados están expresados en soles (S/) e <strong>incluyen el IGV (18%)</strong>,
          conforme a la normativa peruana de precios al consumidor final. Hacemos lo posible por que
          la información de productos, stock y precios sea exacta; ante un error evidente nos reservamos
          el derecho de corregirlo o de cancelar el pedido afectado, notificándote.
        </p>
      </section>

      <section>
        <h2>4. Comprobantes de pago</h2>
        <p>
          Por cada compra emitimos un comprobante: <strong>boleta de venta</strong> (consumidor final,
          con DNI) o <strong>factura</strong> (con RUC y razón social), según lo que elijas al pagar. El
          total a pagar es el mismo en ambos casos; la factura discrimina la base imponible y el IGV.
        </p>
      </section>

      <section>
        <h2>5. Compras, pagos y envíos</h2>
        <p>
          El costo de envío se calcula al confirmar la compra: es gratuito a partir de cierto monto y,
          por debajo de él, se aplica una tarifa fija que verás antes de pagar. Un pedido pasa por los
          estados <strong>pendiente</strong>, <strong>confirmado</strong>, <strong>enviado</strong> y
          <strong> entregado</strong>, y podés seguir su evolución desde “Mis pedidos”.
        </p>
      </section>

      <section>
        <h2>6. Cambios y devoluciones</h2>
        <p>
          Podés solicitar cambios o devoluciones dentro del plazo legal vigente, siempre que el producto
          conserve su estado original. Los productos cancelados antes del envío reponen automáticamente
          el stock. Para gestionar una devolución, contactanos con el número de tu pedido.
        </p>
      </section>

      <section>
        <h2>7. Propiedad intelectual</h2>
        <p>
          La marca Krypton, el logo, los textos y el diseño del sitio son de su titular. No está
          permitido reproducirlos ni usarlos sin autorización.
        </p>
      </section>

      <section>
        <h2>8. Limitación de responsabilidad</h2>
        <p>
          Krypton no será responsable por daños indirectos derivados del uso del sitio, ni por
          interrupciones ajenas a nuestro control. Nuestra responsabilidad se limita, como máximo, al
          importe del pedido involucrado.
        </p>
      </section>

      <section>
        <h2>9. Modificaciones y ley aplicable</h2>
        <p>
          Podemos actualizar estos términos; la versión vigente es la publicada en esta página. Estos
          términos se rigen por la legislación de la República del Perú. El tratamiento de tus datos se
          detalla en nuestra <Link to="/privacidad">Política de privacidad</Link>.
        </p>
      </section>
    </LegalDoc>
  );
}
