import sys

# Leer el archivo
with open(r'src\main\java\com\tpsstudio\view\MainViewController.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Eliminar líneas 172-293 (código huérfano de buildEditPanels)
# Y líneas 295-373 (código huérfano de buildExportPanels)  
# Mantener líneas 0-171 y 374 en adelante

# Primero identificar las líneas a mantener
keep_lines = lines[:172] + lines[374:]

# Escribir el archivo limpio
with open(r'src\main\java\com\tpsstudio\view\MainViewController.java', 'w', encoding='utf-8') as f:
    f.writelines(keep_lines)

print(f"Eliminadas {len(lines) - len(keep_lines)} líneas de código huérfano")
print(f"Archivo reducido de {len(lines)} a {len(keep_lines)} líneas")
