#include <BLEDevice.h>
#include <BLEServer.h>

constexpr uint8_t kNeoPixelPin = 48;
constexpr uint8_t kLevel = 16;
// One-byte BLE values: off, red, green, blue.
constexpr uint8_t kColors[][3] = {{0, 0, 0}, {kLevel, 0, 0},
                                  {0, kLevel, 0}, {0, 0, kLevel}};
constexpr char kDeviceName[] = "DS3870 LED";
constexpr char kServiceId[] = "ac4a9f3c-6c4a-4f33-aaae-b650f17d6390";
constexpr char kLedStateId[] = "ac4a9f3c-6c4a-4f33-aaae-b650f17d6391";

class LedStateCallbacks : public BLECharacteristicCallbacks {
  uint8_t state = 0;

  void onWrite(BLECharacteristic *characteristic) override {
    const auto value = characteristic->getValue();
    if (value.length() == 1) {
      const uint8_t requested = static_cast<uint8_t>(value[0]);
      if (requested < sizeof(kColors) / sizeof(kColors[0])) {
        state = requested;
        const auto& rgb = kColors[state];
        rgbLedWrite(kNeoPixelPin, rgb[0], rgb[1], rgb[2]);
      }
    }
    characteristic->setValue(&state, sizeof(state));
  }
};

class ServerCallbacks : public BLEServerCallbacks {
  void onDisconnect(BLEServer *server) override {
    server->startAdvertising();
  }
};

LedStateCallbacks ledCallbacks;
ServerCallbacks serverCallbacks;

void setup() {
  rgbLedWrite(kNeoPixelPin, 0, 0, 0);
  BLEDevice::init(kDeviceName);
  auto *server = BLEDevice::createServer();
  server->setCallbacks(&serverCallbacks);

  auto *service = server->createService(kServiceId);
  auto *ledState = service->createCharacteristic(
      kLedStateId, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE);
  uint8_t off = 0;
  ledState->setValue(&off, sizeof(off));
  ledState->setCallbacks(&ledCallbacks);
  service->start();

  auto *advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(kServiceId);
  advertising->setScanResponse(true);
  advertising->start();
}

void loop() {
  delay(1000);
}
