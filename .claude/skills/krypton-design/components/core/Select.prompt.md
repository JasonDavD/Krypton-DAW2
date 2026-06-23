Brand-styled dropdown (native select under the hood).

```jsx
<Select label="Categoría" placeholder="Todas las categorías"
        options={["Laptops", "Componentes", "Audio"]} />
<Select options={[{value:1,label:"Laptops"},{value:2,label:"Audio"}]} />
```

Props: `label`, `options` (strings or `{value,label}`), `placeholder`.
