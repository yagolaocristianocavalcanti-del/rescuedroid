# 📱 RescueDroid 2.0-BETA 🍌🤖

![Status](https://shields.io)
![PicoClaw](https://shields.io)

O **RescueDroid** é uma suíte avançada de manutenção e resgate via ADB, agora turbinada com o motor de inteligência do [PicoClaw](https://github.com). 

---

## 🚀 Inovações de Elite

*   **🤖 Cérebro PicoClaw:** Integração direta com a tecnologia da [Sipeed]([https://github.com](https://github.com/sipeed/picoclaw), permitindo processamento de comandos com baixíssimo consumo de RAM (< 10MB) [1] e respostas instantâneas (< 1s) [1].
*   **🌌 Interface Global OLED:** Foco em pretos puros para economia de energia em telas AMOLED.
*   **⚡ Handshake "Martelo":** Protocolo de conexão agressivo para forçar o ADB em dispositivos instáveis.
*   **🧹 Debloat Forense:** Engine que separa o lixo da fabricante do que é vital para o sistema.

---

## 🌳 Estrutura do Projeto

```text
rescuedroid2/
├── app/src/main/java/com/rescuedroid/rescuedroid/
│   ├── adb/                 # 🔌 NÚCLEO ADB (Coração)
│   │   ├── UsbAdbConnector.kt  # Handshake USB (Normal, Turbo, Martelo)
│   │   └── AdbKeyManager.kt    # Gestão de chaves RSA
│   │
│   ├── ai/                  # 🤖 PICO CLAW (Cérebro)
│   │   ├── IAEscuta.kt         # Parser de voz via PicoClaw
│   │   └── IACmd.kt            # Orquestração de comandos IA
│   │
│   ├── ui/                  # 🎨 INTERFACE (Compose)
│   │   ├── SupportScreen.kt    # Chat IA e Voz
│   │   └── ScrcpyScreen.kt     # Mirroring
│   │
│   └── debloat/             # 🧹 LIMPEZA
│       └── DebloatRiskEngine.kt# Analisador de risco
