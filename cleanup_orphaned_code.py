import sys

# Leer el archivo
with open(r'src\main\java\com\tpsstudio\view\MainViewController.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Eliminar líneas 267-631 (índices 266-630 en Python, 0-indexed)
# Mantener líneas 0-265 y 632 en adelante
new_lines = lines[:266] + lines[632:]

# Escribir el archivo limpio
with open(r'src\main\java\com\tpsstudio\view\MainViewController.java', 'w', encoding='utf-8') as f:
    f.writelines(new_lines)

print(f"Eliminadas {len(lines) - len(new_lines)} líneas de código huérfano")
print(f"Archivo reducido de {len(lines)} a {len(new_lines)} líneas")
