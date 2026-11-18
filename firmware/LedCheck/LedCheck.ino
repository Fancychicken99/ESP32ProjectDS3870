// GPIO 48 was confirmed on the project board with this blink sketch.
constexpr uint8_t kNeoPixelPin = 48;
constexpr uint8_t kRedLevel = 16;

void setup() {
  rgbLedWrite(kNeoPixelPin, 0, 0, 0);
}

void loop() {
  rgbLedWrite(kNeoPixelPin, kRedLevel, 0, 0);
  delay(1000);
  rgbLedWrite(kNeoPixelPin, 0, 0, 0);
  delay(1000);
}
