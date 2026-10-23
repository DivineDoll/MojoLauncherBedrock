# Terracotta / licencia de este fork

La función **LAN con amigos** de MojoLauncherBedrock usa **Terracotta**
(© 2025 Burning_TNT, https://github.com/burningtnt/Terracotta), que funciona sobre
**EasyTier** (https://github.com/EasyTier/EasyTier).

Terracotta se distribuye bajo la **GNU Affero General Public License v3.0 (AGPL-3.0)**.
Por eso, **la versión de este fork que incluye Terracotta se distribuye bajo AGPL-3.0**
(el código original de MojoLauncher es LGPL-3.0, que permite este cambio).

Qué incluye este fork de Terracotta:

- `app_mojo/src/main/java/net/burningtnt/terracotta/TerracottaAndroidAPI.java`: API Java sin modificar.
- `app_mojo/src/main/jniLibs/*/libterracotta.so`: librería nativa de Terracotta para Android.
  Su código fuente está en https://github.com/burningtnt/Terracotta.
- `app_mojo/src/main/java/git/artdeell/mojo/lan/`: código propio de este fork que usa Terracotta.
  El servicio VPN se adaptó del de Zalith Launcher 2 (GPL-3.0,
  https://github.com/ZalithLauncher/ZalithLauncher2), que a su vez se basa en FoldCraftLauncher.

El texto completo de la AGPL-3.0 está en el archivo `LICENSE-AGPL-3.0` de la raíz.

Minecraft es una marca de Mojang Studios / Microsoft. Este proyecto no está afiliado ni aprobado por Mojang ni por Microsoft.
