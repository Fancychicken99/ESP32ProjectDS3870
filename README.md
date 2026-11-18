# ESP32ProjectDS3870
ESP 32 S3 project for DS3870

The first milestone is to connect from a custom Android app and toggle the
board's onboard RGB LED. The project uses Bluetooth Low Energy (BLE).

The first milestone was verified on a BLU G50 Plus running Android 10.
The custom app connected to the ESP32 over BLE and toggled the onboard RGB LED.

## Check the onboard LED

`firmware/LedCheck` is a standalone Arduino sketch. It uses the Arduino-ESP32
3.x core's `rgbLedWrite` function to alternate between dim red and off each
second. The red blink after upload and reset confirmed GPIO 48 on the project board.

From the repository root in PowerShell, with Arduino CLI available:

```powershell
arduino-cli core update-index --additional-urls https://espressif.github.io/arduino-esp32/package_esp32_index.json
arduino-cli core install esp32:esp32@3.3.13 --additional-urls https://espressif.github.io/arduino-esp32/package_esp32_index.json
arduino-cli board details --fqbn esp32:esp32:esp32s3
arduino-cli compile --fqbn esp32:esp32:esp32s3 .\firmware\LedCheck
arduino-cli board list
```

If using the CLI prepared in the Windows temporary folder, these commands use
its existing installation without changing the system PATH:

```powershell
$tools = Join-Path $env:TEMP 'esp32project-tools'
$cli = Join-Path $tools 'arduino-cli.exe'
$config = Join-Path $tools 'arduino-cli.yaml'
& $cli --config-file $config compile --fqbn esp32:esp32:esp32s3 .\firmware\LedCheck
& $cli --config-file $config board list
```

`esp32:esp32:esp32s3` selects **ESP32S3 Dev Module**. The compile command
above checks the sketch using the profile defaults and needs no connected board.
Espressif's `esptool flash-id` identified this board as an ESP32-S3 with 16 MB
quad flash, 8 MB embedded PSRAM, and native USB Serial/JTAG.

Connect the board with a USB data cable. In `arduino-cli board list`, compare
the ports before and after plugging it in to identify its COM port. In Arduino
IDE, the equivalent setup uses **Boards Manager** to install **esp32 by
Espressif Systems**, then the **Tools** menu to select **ESP32S3 Dev Module**,
its board options, and its port.

If the uploader cannot connect, hold **BOOT**, press and release **RESET**
(or **EN**), then release **BOOT**. Check the ports again. This board appeared
as COM3 while running and COM4 in download mode. Windows may assign different
port numbers after a reconnect.

Use the same settings for compilation and upload. For this board, select 16 MB
flash, QIO at 80 MHz, the 16 MB partition scheme with a 3 MB app slot, OPI PSRAM,
Hardware CDC and JTAG, USB CDC on boot, and upload speed 115200. Replace
`COM4` below with the current download-mode port. If using the temporary CLI
installation, substitute `& $cli --config-file $config` for `arduino-cli`.

```powershell
$board = 'esp32:esp32:esp32s3:FlashSize=16M,FlashMode=qio,PartitionScheme=app3M_fat9M_16MB,PSRAM=opi,USBMode=hwcdc,CDCOnBoot=cdc,UploadSpeed=115200'
$port = 'COM4'
arduino-cli compile --fqbn $board .\firmware\LedCheck
arduino-cli upload --port $port --fqbn $board .\firmware\LedCheck
```

After upload, press and release **RESET** with **BOOT** untouched if the sketch
doesn't start. The onboard RGB LED should alternate between dim red and off
once a second.

## Control the LED over BLE

Build and upload `.\firmware\BleLed` using the same commands and settings.
The LED starts off. The board advertises as **DS3870 LED**.

| BLE item | Value |
| --- | --- |
| Service UUID | `ac4a9f3c-6c4a-4f33-aaae-b650f17d6390` |
| LED state characteristic UUID | `ac4a9f3c-6c4a-4f33-aaae-b650f17d6391` |
| Read | One byte: `00` off, `01` red, `02` green, or `03` blue |
| Write with response | One raw hex byte: `00` off, `01` red, `02` green, or `03` blue |

To test from Android, turn on Bluetooth and use a BLE scanner such as Nordic
Semiconductor's nRF Connect for Mobile. Grant the requested Bluetooth
permissions, scan for **DS3870 LED**, and connect. Open the service and LED state
characteristic listed above. Write `01` in byte array or hex format using
**Write Request**. Watch for the LED to turn dim red, then read the characteristic
and check for `01`. Write `00` and check that the LED goes dark and the
characteristic reads `00`. Disconnect from the scanner before using the custom app.

## Build and test the custom Android app

The native Java app in `android` requires Android 10 or later and a phone that
supports BLE. Open that folder as a project in Android Studio, using JDK 17
and Android SDK Platform 35. Alternatively, with JDK 17, the SDK, and Gradle
8.9 available in PowerShell, run this from the repository root:

```powershell
gradle -p android assembleDebug
```

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.
Install it using Android Studio's **Run** button or use the following commands
with Android SDK Platform-Tools. For a USB install, enable USB debugging on
your phone, connect it with a data cable, and accept the prompt on the phone.

```powershell
adb devices
adb install -r .\android\app\build\outputs\apk\debug\app-debug.apk
```

You can also select **File transfer** on the phone, copy the APK to the
phone's **Download** folder in Windows File Explorer, then open the APK
from the phone's Files app. Android may ask you to allow that Files app
to install apps from this source. On the BLU G50 Plus, Files showed the APK
under **Applications**. Opening it there worked after Android scanned it.

1. Power the ESP32 with the BLE sketch running. Turn Bluetooth on and open
   **DS3870 LED** on the phone.
2. Tap **Connect**. On Android 12 or later, allow **Nearby Devices**. On
   Android 10 or 11, turn on **Location** and grant location access while
   using the app so Android allows BLE scanning. The app searches for the
   project service and reads the current LED state.
3. Tap **Red**, **Green**, and **Blue** in turn. Check that the onboard LED
   matches the app's reported color each time. Tap **Off** and check that it
   goes dark. Green and blue require the updated BLE sketch on the ESP32.
4. Disconnect and connect again to check that the app finds the board and
   shows the current state.
