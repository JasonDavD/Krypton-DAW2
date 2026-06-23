Labeled text field with focus halo, helper text and error state.

```jsx
<Input label="Correo" type="email" placeholder="tu@correo.com"
       iconLeft={<i data-lucide="mail" />} />
<Input label="Contraseña" type="password" error="Mínimo 8 caracteres" />
```

Props: `label`, `helper`, `error`, `iconLeft`, plus all native input attrs.
