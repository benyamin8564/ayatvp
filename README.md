# AI-Iran-VPN-Full

نسخه 3.0 با **WireGuard واقعی** بر پایه کتابخانه رسمی WireGuard Android.

## قابلیت‌ها
- WireGuard واقعی، نه skeleton و نه tunnel سفارشی
- تولید کلید خصوصی کلاینت روی خود دستگاه
- نگهداری کلید خصوصی با Android Keystore و AES-GCM
- پشتیبانی از چند سرور
- انتخاب دستی سرور
- انتخاب خودکار سرور بر اساس HTTPS health check و latency
- کش محلی کانفیگ سرورها
- دریافت کانفیگ از HTTPS با بررسی اختیاری SHA-256
- GitHub Actions برای Build APK

## نکته مهم
APK به‌تنهایی شامل سرور VPN نیست. برای اتصال عملی باید Gateway WireGuard تحت کنترل خودتان داشته باشید و `app/src/main/assets/servers.json` را با endpoint و public key واقعی آن سرورها تنظیم کنید.

این پروژه از پروتکل استاندارد WireGuard استفاده می‌کند و عمداً هیچ پروتکل اختصاصی/مبهم‌سازی ترافیک برای دورزدن DPI اضافه نمی‌کند.

کتابخانه tunnel رسمی WireGuard برای Android روی Maven Central منتشر می‌شود و قابلیت embedding دارد.
