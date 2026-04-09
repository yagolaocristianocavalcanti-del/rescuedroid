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
│   ├── adb/                 # 🔌 NÚCLEO ADB (O "Coração" do App)
│   │   ├── AdbManager.kt       # Execução de comandos e estados
│   │   ├── UsbAdbConnector.kt  # Handshake USB (Normal, Forte, Martelo)
│   │   ├── AdbKeyManager.kt    # Gestão de chaves RSA e Autorização
│   │   └── HistoryManager.kt   # Histórico de dispositivos e conexões
│   │
│   ├── ai/                  # 🤖 INTELIGÊNCIA ARTIFICIAL (PicoClaw)
│   │   ├── IAEscuta.kt         # Parser de voz e linguagem natural
│   │   └── IACmd.kt            # Definição de intenções e comandos
│   │
│   ├── ui/                  # 🎨 INTERFACE (Jetpack Compose)
│   │   ├── MainActivity.kt     # Container Principal e FAB Global
│   │   ├── SupportScreen.kt    # Chat IA e Comandos de Voz
│   │   ├── DebloatScreen.kt    # Gerenciador de Apps e Risco
│   │   ├── AutomationScreen.kt # Editor de Scripts Shell (.sh)
│   │   └── ScrcpyScreen.kt     # Espelhamento de Tela (Mirror)
│   │
│   ├── viewmodel/           # 🧠 LÓGICA DE ESTADO (MVVM)
│   │   └── MainViewModel.kt    # Orquestrador de dados e UI
│   │
│   ├── components/          # ✨ COMPONENTES VISUAIS
│   │   └── HackerModeOverlay.kt# Efeitos visuais e animações
│   │
│   └── debloat/             # 🧹 ENGINE DE LIMPEZA
│       └── DebloatRiskEngine.kt# Analisador de segurança de pacotes
│
├── bin/                     # 📦 DISTRIBUIÇÃO
│   └── RescueDroid-2.0-BETA.apk # Instalador atualizado
│
└── build.gradle.kts         # ⚙️ CONFIGURAÇÕES (Version 2.0-BETA)
