import { Link } from 'react-router-dom';
import { LegalDoc } from './LegalDoc';

/** Política de privacidad de Krypton. */
export function PrivacyPage() {
  return (
    <LegalDoc title="Política de privacidad" updated="19 de junio de 2026">
      <section>
        <h2>1. Qué datos recopilamos</h2>
        <p>Para operar la tienda tratamos los siguientes datos:</p>
        <ul>
          <li><strong>De tu cuenta:</strong> nombre, correo electrónico y contraseña (almacenada de forma cifrada).</li>
          <li><strong>De tus compras:</strong> productos, montos, y los datos del comprobante (DNI o RUC y razón social).</li>
          <li><strong>De uso:</strong> datos técnicos de navegación necesarios para que el sitio funcione.</li>
        </ul>
      </section>

      <section>
        <h2>2. Para qué los usamos</h2>
        <p>
          Usamos tus datos para crear y gestionar tu cuenta, procesar tus pedidos y pagos, emitir los
          comprobantes correspondientes, darte seguimiento de tus compras y brindarte soporte. No usamos
          tus datos para fines distintos a los aquí descritos sin tu consentimiento.
        </p>
      </section>

      <section>
        <h2>3. Con quién los compartimos</h2>
        <p>
          No vendemos tus datos. Sólo los compartimos con terceros cuando es estrictamente necesario para
          prestar el servicio (por ejemplo, el operador logístico que entrega tu pedido) o cuando la ley
          lo exige.
        </p>
      </section>

      <section>
        <h2>4. Tus derechos</h2>
        <p>
          De acuerdo con la <strong>Ley N.° 29733, Ley de Protección de Datos Personales</strong> del Perú
          y su reglamento, tenés derecho a acceder, rectificar, cancelar y oponerte al tratamiento de tus
          datos (derechos ARCO). Podés ejercerlos escribiéndonos; atenderemos tu solicitud en los plazos
          que establece la norma.
        </p>
      </section>

      <section>
        <h2>5. Seguridad</h2>
        <p>
          Aplicamos medidas técnicas y organizativas razonables para proteger tus datos. Las contraseñas
          se guardan cifradas y el acceso a la información está restringido al personal autorizado. Ningún
          sistema es 100% infalible, pero trabajamos para minimizar los riesgos.
        </p>
      </section>

      <section>
        <h2>6. Conservación</h2>
        <p>
          Conservamos tus datos mientras mantengas tu cuenta y durante los plazos que la legislación exige
          (por ejemplo, los vinculados a comprobantes y obligaciones tributarias).
        </p>
      </section>

      <section>
        <h2>7. Cambios y contacto</h2>
        <p>
          Podemos actualizar esta política; la versión vigente es la publicada en esta página. Para
          cualquier consulta sobre tus datos o sobre nuestros{' '}
          <Link to="/terminos">Términos y condiciones</Link>, podés contactarnos a través de los canales
          de la tienda.
        </p>
      </section>
    </LegalDoc>
  );
}
