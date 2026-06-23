// Krypton storefront — demo catalog data (es-PE, prices in Soles).
window.KR_DATA = (function () {
  const products = [
    { id: 1, sku: "KRP-NB-2048", name: "Laptop Krypton UltraBook 14\" Core i7", category: "Laptops", icon: "laptop", price: 4299.0, oldPrice: 4899.0, rating: 4.5, ratingCount: 214, stock: 12, badge: { label: "-12%", tone: "sale" }, brand: "Krypton", desc: "Pantalla IPS 14'' 2.5K, Intel Core i7 de 13.ª gen, 16 GB RAM, SSD 1 TB. Ultraligera y potente para trabajo y estudio." },
    { id: 2, sku: "KRP-PH-7710", name: "Smartphone Krypton Neo 5G 256GB", category: "Smartphones", icon: "smartphone", price: 1899.0, oldPrice: null, rating: 4.0, ratingCount: 98, stock: 30, badge: { label: "Nuevo", tone: "brand" }, brand: "Krypton", desc: "Pantalla AMOLED 6.7'' 120Hz, cámara triple 108 MP, batería 5000 mAh con carga rápida de 67W." },
    { id: 3, sku: "KRP-AU-0042", name: "Audífonos Krypton Pulse X Bluetooth", category: "Audio", icon: "headphones", price: 349.9, oldPrice: 429.9, rating: 4.5, ratingCount: 128, stock: 45, badge: { label: "-20%", tone: "sale" }, brand: "Krypton", desc: "Cancelación activa de ruido, 40h de autonomía, Bluetooth 5.3 y modo de baja latencia para gaming." },
    { id: 4, sku: "KRP-CP-9001", name: "Procesador Ryzen 7 7800X3D", category: "Componentes", icon: "cpu", price: 1799.0, oldPrice: null, rating: 5.0, ratingCount: 342, stock: 8, badge: null, brand: "AMD", desc: "8 núcleos / 16 hilos, tecnología 3D V-Cache, ideal para gaming de alto rendimiento. Socket AM5." },
    { id: 5, sku: "KRP-MS-3320", name: "Mouse Gamer Krypton Viper RGB", category: "Periféricos", icon: "mouse", price: 159.9, oldPrice: 199.9, rating: 4.0, ratingCount: 76, stock: 60, badge: { label: "-20%", tone: "sale" }, brand: "Krypton", desc: "Sensor óptico 26K DPI, 6 botones programables, iluminación RGB y switches de 70M de clics." },
    { id: 6, sku: "KRP-KB-3322", name: "Teclado Mecánico Krypton Forge TKL", category: "Periféricos", icon: "keyboard", price: 289.9, oldPrice: null, rating: 4.5, ratingCount: 54, stock: 25, badge: null, brand: "Krypton", desc: "Switches hot-swap, estructura de aluminio, retroiluminación RGB por tecla y conexión USB-C." },
    { id: 7, sku: "KRP-MN-2701", name: "Monitor Krypton Vision 27\" 165Hz", category: "Monitores", icon: "monitor", price: 1099.0, oldPrice: 1299.0, rating: 4.5, ratingCount: 187, stock: 14, badge: { label: "-15%", tone: "sale" }, brand: "Krypton", desc: "Panel IPS QHD 27'', 165Hz, 1ms, HDR400 y soporte ergonómico ajustable. FreeSync Premium." },
    { id: 8, sku: "KRP-WT-1180", name: "Smartwatch Krypton Active 2", category: "Smartwatches", icon: "watch", price: 499.0, oldPrice: null, rating: 4.0, ratingCount: 63, stock: 40, badge: { label: "Nuevo", tone: "brand" }, brand: "Krypton", desc: "GPS integrado, SpO2, monitor de sueño, 14 días de batería y más de 100 modos deportivos." },
    { id: 9, sku: "KRP-GP-5567", name: "Control Inalámbrico Krypton Pro", category: "Gaming", icon: "gamepad-2", price: 249.9, oldPrice: 299.9, rating: 4.5, ratingCount: 145, stock: 33, badge: { label: "-17%", tone: "sale" }, brand: "Krypton", desc: "Gatillos hall-effect, paletas traseras reasignables, batería recargable y conexión 2.4G / Bluetooth." },
    { id: 10, sku: "KRP-SD-4096", name: "SSD NVMe Krypton Rapid 2TB Gen4", category: "Componentes", icon: "hard-drive", price: 729.0, oldPrice: 849.0, rating: 5.0, ratingCount: 211, stock: 18, badge: { label: "-14%", tone: "sale" }, brand: "Krypton", desc: "Lectura hasta 7400 MB/s, disipador incluido, PCIe 4.0 x4. Ideal para PS5 y PCs de alto rendimiento." },
    { id: 11, sku: "KRP-TB-1011", name: "Tablet Krypton Pad 11\" 128GB", category: "Smartphones", icon: "tablet", price: 1299.0, oldPrice: null, rating: 4.0, ratingCount: 41, stock: 22, badge: null, brand: "Krypton", desc: "Pantalla 2K 11'', soporte para lápiz óptico, 8 GB RAM y 4 altavoces con sonido envolvente." },
    { id: 12, sku: "KRP-SP-2230", name: "Parlante Krypton Boom Portátil", category: "Audio", icon: "speaker", price: 219.9, oldPrice: 269.9, rating: 4.5, ratingCount: 89, stock: 50, badge: { label: "-18%", tone: "sale" }, brand: "Krypton", desc: "60W, resistencia al agua IPX7, 24h de reproducción y emparejamiento estéreo dual." },
  ];

  const categories = [
    { name: "Laptops", icon: "laptop" },
    { name: "Smartphones", icon: "smartphone" },
    { name: "Audio", icon: "headphones" },
    { name: "Componentes", icon: "cpu" },
    { name: "Periféricos", icon: "mouse" },
    { name: "Monitores", icon: "monitor" },
    { name: "Gaming", icon: "gamepad-2" },
    { name: "Smartwatches", icon: "watch" },
  ];

  const fmt = (n) =>
    "S/ " + Number(n).toLocaleString("es-PE", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  return { products, categories, fmt };
})();
