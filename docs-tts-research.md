# TTS فارسی آفلاین — یافته‌ها

تاریخ بررسی: 2026-08-22

## گزینه‌های بررسی‌شده

- eSpeak NG: روی گوشی کاربر نصب است، اما Android آن فقط voiceهای لهستانی، انگلیسی و روسی را گزارش می‌کند و voice فارسی ندارد.
- `gyroing/Persian-Piper-Model-gyro`: مدل Piper فارسی با برچسب MIT و ONNX، اما صفحه Hugging Face اعلام می‌کند یک فایل آن به‌عنوان unsafe اسکن شده است؛ بدون بررسی و اعتبارسنجی نباید مستقیماً وارد APK شود.
- `facebook/mms-tts-fas`: مدل رسمی فارسی MMS/VITS از Meta با 36.3M پارامتر و مجوز CC-BY-NC-4.0. مدل فارسی واقعی است، اما مجوز آن برای استفاده تجاری مناسب نیست و برای اجرای Android باید به فرمت/Runtime مناسب مانند sherpa-onnx تبدیل شود.
- sherpa-onnx: Runtime دارای مستندات Android/Kotlin و پشتیبانی از مدل‌های TTS مانند VITS/Piper/MMS است.

## تصمیم موقت

برای استفاده شخصی و غیرتجاری، `facebook/mms-tts-fas` گزینه رسمی‌تر و قابل‌اعتمادتر است؛ برای انتشار پولی باید مجوز مدل بررسی و احتمالاً یک مدل با مجوز تجاری انتخاب شود. مدل داخلی باعث افزایش محسوس حجم APK و مصرف RAM/CPU می‌شود. ادغام واقعی نیازمند تبدیل/بسته‌بندی model و runtime است و صرفاً تغییر TextToSpeech سیستم کافی نیست.

## منابع

- https://huggingface.co/facebook/mms-tts-fas
- https://huggingface.co/gyroing/Persian-Piper-Model-gyro
- https://k2-fsa.github.io/sherpa/onnx/tts/index.html
- https://github.com/espeak-ng/espeak-ng

## جزئیات ادغام Android

مستندات رسمی sherpa-onnx نمونه Android و Kotlin API ارائه می‌کند. کلاس `OfflineTts` با `OfflineTtsConfig` از مدل‌های VITS پشتیبانی می‌کند و خروجی را به‌صورت `GeneratedAudio(samples, sampleRate)` برمی‌گرداند؛ سپس برنامه باید samples را با AudioTrack پخش کند. کتابخانه JNI/AAR رسمی از releaseهای sherpa-onnx قابل ساخت است و معماری‌های Android را پوشش می‌دهد.

مخزن `willwade/mms-tts-multilingual-models-onnx` شامل مسیر `fas/model.onnx`، `fas/tokens.txt` و `fas/sample.wav` است. مجوز مخزن در API برابر CC-BY-NC-4.0 گزارش شده است. مدل رسمی فارسی `facebook/mms-tts-fas` نیز CC-BY-NC-4.0 است و 36.3M پارامتر دارد.

منابع تکمیلی:

- https://k2-fsa.github.io/sherpa/onnx/android/build-sherpa-onnx.html
- https://k2-fsa.github.io/sherpa/onnx/tts/index.html
- https://huggingface.co/willwade/mms-tts-multilingual-models-onnx
- https://huggingface.co/facebook/mms-tts-fas
