#!/usr/bin/env python3
"""Pleos Connect Virtual Console에 주행 시나리오를 재생한다 (시연·녹화용).

사전 준비: 에뮬레이터 실행 후 `adb forward tcp:8088 tcp:8088`
사용: python3 tools/drive_demo.py [시나리오]
  smooth  부드러운 주행 (쏟지 않음)
  rough   급제동·회생제동·급조향이 섞인 주행 (기본값)
  park    P단으로 되돌리기만

Virtual Console은 브라우저가 열려 있을 때만 값을 받으므로,
재생하는 동안 이 스크립트가 브라우저 세션 heartbeat를 대신 보낸다.
"""
import sys
import threading
import time
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1:8088"
CLIENT = "watercup-demo"
HZ = 10


def call(method, path, **params):
    url = BASE + path + ("?" + urllib.parse.urlencode(params) if params else "")
    req = urllib.request.Request(url, method=method, data=b"" if method != "GET" else None)
    try:
        with urllib.request.urlopen(req, timeout=3) as res:
            return res.read().decode("utf-8", "replace")
    except Exception as e:  # 시연 중 한두 번 실패해도 계속 진행
        return f"ERR {e}"


def heartbeat(stop):
    while not stop.is_set():
        call("POST", "/browser/session", client=CLIENT)
        stop.wait(1.0)


class Car:
    def __init__(self):
        self.speed = 0.0
        self.steering = 0.0

    def send(self, brake=0, accelerator=0):
        call(
            "PUT",
            "/vehicle/controls",
            speed=str(int(round(self.speed))),  # 정수만 받는다
            accelerator=str(accelerator),
            brake=str(brake),
            steering=f"{self.steering:.0f}",
        )

    def gear(self, name):
        print(f"  기어 {name}")
        print("   ", call("PUT", "/vehicle/gear", gear=name)[:80])
        time.sleep(1.0)

    def ramp(self, to, seconds, brake=0, accelerator=0, label=""):
        """현재 속도에서 to(km/h)까지 seconds 동안 일정하게 변속."""
        if label:
            print(f"  {label}: {self.speed:.0f} → {to} km/h, {seconds}s")
        start = self.speed
        steps = max(1, int(seconds * HZ))
        for i in range(1, steps + 1):
            self.speed = start + (to - start) * i / steps
            self.send(brake=brake, accelerator=accelerator)
            time.sleep(1 / HZ)

    def hold(self, seconds, label=""):
        self.ramp(self.speed, seconds, label=label)

    def steer(self, to, seconds, label=""):
        if label:
            print(f"  {label}: 핸들 {to}°")
        start = self.steering
        steps = max(1, int(seconds * HZ))
        for i in range(1, steps + 1):
            self.steering = start + (to - start) * i / steps
            self.send()
            time.sleep(1 / HZ)


def smooth(car):
    car.gear("Drive")
    car.ramp(50, 12, accelerator=15, label="부드러운 출발")
    car.hold(4, "정속")
    car.steer(15, 2.5, "완만한 우회전")
    car.steer(0, 2.5)
    car.ramp(0, 12, brake=15, label="부드러운 정지")
    car.gear("Park")


def rough(car):
    car.gear("Drive")
    car.ramp(60, 4, accelerator=70, label="급가속")
    car.hold(3, "정속")
    car.ramp(25, 2, label="가속 페달 급해제(회생제동)")
    car.hold(3)
    car.ramp(55, 8, accelerator=20, label="부드러운 재가속")
    car.steer(95, 0.6, "급조향")
    car.hold(1.5)
    car.steer(0, 0.8)
    car.hold(3)
    car.ramp(0, 2.5, brake=80, label="급제동")
    car.hold(2)
    car.gear("Park")


def main():
    name = sys.argv[1] if len(sys.argv) > 1 else "rough"
    stop = threading.Event()
    threading.Thread(target=heartbeat, args=(stop,), daemon=True).start()
    time.sleep(1.5)
    car = Car()
    print(f"시나리오: {name}")
    try:
        if name == "smooth":
            smooth(car)
        elif name == "park":
            car.send()
            car.gear("Park")
        else:
            rough(car)
    finally:
        car.speed = 0
        car.steering = 0
        car.send()
        stop.set()
    print("끝")


if __name__ == "__main__":
    main()
