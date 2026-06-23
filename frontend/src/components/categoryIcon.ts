import { Cpu, Gamepad2, Headphones, Keyboard, Laptop, Monitor, Package, Speaker, type LucideIcon } from 'lucide-react';

/** Icono de lucide para una categoría, por heurística de nombre (respaldo: Package). */
export function iconForCategory(name: string): LucideIcon {
  const n = name.toLowerCase();
  if (n.includes('laptop') || n.includes('notebook')) return Laptop;
  if (n.includes('component')) return Cpu;
  if (n.includes('audio') || n.includes('audíf') || n.includes('audif')) return Headphones;
  if (n.includes('parlante') || n.includes('speaker')) return Speaker;
  if (n.includes('monitor') || n.includes('pantalla')) return Monitor;
  if (n.includes('perifér') || n.includes('perifer') || n.includes('teclado') || n.includes('mouse')) return Keyboard;
  if (n.includes('gaming') || n.includes('gamer') || n.includes('consola')) return Gamepad2;
  return Package;
}
