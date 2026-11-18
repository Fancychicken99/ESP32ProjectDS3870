#include <BLEDevice.h>
#include <BLEServer.h>

constexpr uint8_t kNeoPixelPin = 48;
constexpr uint8_t kRedLevel = 16;
constexpr char kDeviceName[] = "DS3870 LED";
constexpr char kServiceId[] = "ac4a9f3c-6c4a-4f33-aaae-b650f17d6390";
constexpr char kLedStateId[] = "ac4a9f3c-6c4a-4f33-aaae-b650f17d6391";

class LedStateCallbacks : public BLECharacteristicCallbacks {
  uint8_t state = 0;

  void onWrite(BLECharacteristic *characteristic) override {
    const auto value = characteristic->getValue();
    if (value.length() == 1 && (value[0] == 0 || value[0] == 1)) {
      state = value[0];
      rgbLedWrite(kNeoPixelPin, state == 1 ? kRedLevel : 0, 0, 0);
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
