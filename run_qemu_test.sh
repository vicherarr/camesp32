#!/usr/bin/env bash
# ==============================================================================
# Script de Prueba QEMU para ESP32 Repeater y ESP32-S3 Camera
# ==============================================================================

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

QEMU_BIN="/home/victor/.espressif/tools/qemu-xtensa/esp_develop_9.0.0_20240606/qemu/bin/qemu-system-xtensa"
PROJECT_DIR="/home/victor/develop/iot/camesp32"

export LD_LIBRARY_PATH="/tmp:${LD_LIBRARY_PATH}"

echo -e "${BLUE}====================================================${NC}"
echo -e "${BLUE}  Prueba de Emulación QEMU: ESP32 Repeater & Cam   ${NC}"
echo -e "${BLUE}====================================================${NC}"

if [ ! -f "$QEMU_BIN" ]; then
    echo -e "${RED}Error: No se encontró el ejecutable QEMU en ${QEMU_BIN}${NC}"
    exit 1
fi

echo -e "\n${YELLOW}Selecciona el proyecto que deseas emular:${NC}"
echo "1) ESP32 Repeater (NAT Extender) [-machine esp32]"
echo "2) ESP32-S3 Cam Security Project  [-machine esp32s3]"
echo "3) Compilar ambos e iniciar sesión de emulación"
echo "q) Salir"
read -p "Opción [1-3]: " option

build_repeater() {
    echo -e "\n${BLUE}[1/2] Compilando e imantando ESP32 Repeater para QEMU...${NC}"
    cd "${PROJECT_DIR}/esp32_repeater"
    cargo espflash save-image --release --features qemu-sim --chip esp32 --merge repeater_qemu.bin
    echo -e "${GREEN}✓ Imagen repeater_qemu.bin creada exitosamente.${NC}"
}

build_cam() {
    echo -e "\n${BLUE}[2/2] Compilando e imantando ESP32-S3 Cam para QEMU...${NC}"
    cd "${PROJECT_DIR}/esp32_cam_sec"
    cargo espflash save-image --release --features qemu-sim --chip esp32s3 --merge cam_qemu.bin
    echo -e "${GREEN}✓ Imagen cam_qemu.bin creada exitosamente.${NC}"
}

run_repeater() {
    echo -e "\n${GREEN}Iniciando QEMU para ESP32 Repeater...${NC}"
    echo -e "${YELLOW}(Para salir de QEMU presiona Ctrl + A y luego X)${NC}\n"
    sleep 2
    cd "${PROJECT_DIR}"
    ${QEMU_BIN} -machine esp32 -nographic -drive file=esp32_repeater/repeater_qemu.bin,if=mtd,format=raw
}

run_cam() {
    echo -e "\n${GREEN}Iniciando QEMU para ESP32-S3 Cam...${NC}"
    echo -e "${YELLOW}(Para salir de QEMU presiona Ctrl + A y luego X)${NC}\n"
    sleep 2
    cd "${PROJECT_DIR}"
    ${QEMU_BIN} -machine esp32s3 -nographic -drive file=esp32_cam_sec/cam_qemu.bin,if=mtd,format=raw
}

case $option in
    1)
        build_repeater
        run_repeater
        ;;
    2)
        build_cam
        run_cam
        ;;
    3)
        build_repeater
        build_cam
        echo -e "\n${YELLOW}Elige cuál emular ahora:${NC}"
        echo "a) Repeater"
        echo "b) Cámara"
        read -p "Elección [a/b]: " subopt
        if [ "$subopt" == "a" ]; then
            run_repeater
        else
            run_cam
        fi
        ;;
    q|Q)
        echo "Cancelado."
        exit 0
        ;;
    *)
        echo -e "${RED}Opción inválida.${NC}"
        exit 1
        ;;
esac
