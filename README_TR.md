<div align="center">

<img src="https://github.com/user-attachments/assets/dc034ec0-90cf-4371-9ab0-132ca2527b32" width="120" height="120" style="border-radius: 24px;" alt="HyperIsland İkonu"/>

# HyperIsland

**HyperOS 3 için tasarlanmış Süper Ada ilerleme bildirimi geliştirme modülü**

[![GitHub Release](https://img.shields.io/github/v/release/1812z/HyperIsland?style=flat-square&logo=github&color=black)](https://github.com/1812z/HyperIsland/releases)
![Downloads](https://img.shields.io/github/downloads/1812z/HyperIsland/total?style=flat-square)
[![License](https://img.shields.io/github/license/1812z/HyperIsland?style=flat-square&color=orange)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)](https://android.com)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-blueviolet?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![HyperOS](https://img.shields.io/badge/ROM-HyperOS3-orange?style=flat-square)](https://hyperos.mi.com)
[![Build](https://img.shields.io/badge/Build-Flutter-02569B?style=flat-square&logo=flutter)](https://flutter.dev)


**[English](README_EN.md)** | **[简体中文](README.md)** | **[日本語](README_JA.md)** | **Türkçe**

</div>

---

## ✨ Özellikler

<table>
<tr>
<td width="50%">

### 📥 İndirme Yöneticisi Uzantısı
HyperOS İndirme Yöneticisi bildirimlerini engeller ve dosya adını ile indirme ilerlemesini Süper Ada (Super Island) stilinde gösterir. **Duraklatma, Devam Etme ve İptal Etme** işlemlerini destekler.

</td>
<td width="50%">

### 🏝️ Süper Ada + Odak Bildirimi Uyumluluğu
Herhangi bir uygulama tarafından gönderilen standart Android bildirimlerini engeller, işler ve orijinal bildirimin düğmeleriyle birlikte Süper Ada + Odak Bildirimi (Focus Notification) stilinde görüntüler.

</td>
</tr>
<tr>
<td width="50%">

### 🚫 Bildirim Kara Listesi
Kara listedeki uygulamalar bildirim açılır pencereleri üretmez, yalnızca bir Süper Ada gösterir (tam ekran modunda durum çubuğu ile otomatik olarak gizlenir).

</td>
<td width="50%">

### 🔥 Hot Reload Desteği
Yapılandırma değişiklikleri **yeniden başlatmaya gerek kalmadan** anında geçerli olur. Yeni bir yazılım yükledikten veya güncelledikten sonra yalnızca uygulamanın etki alanını (scope) yeniden başlatın.

</td>
</tr>
</table>

---

## 📋 Kullanım Talimatları

### Adım 1: LSPosed'da Modülü Etkinleştirin

> ⚠️ Bu modül **LSPosed** çerçevesine dayanır. Cihazınızın Root erişimi olması ve LSPosed yüklü olması gerekir.

1. **LSPosed** yöneticisini açın ve "Modüller" (Modules) listesine gidin.
2. **HyperIsland** uygulamasını bulun ve anahtarı etkinleştirin.
3. Modülün etki alanında (scope), önerilen uygulamaları işaretleyin:
   - **İndirme Bildirimleri**: "İndirme Yöneticisi"ni (Download Manager) seçin
   - **Genel Uyumluluk**: "Sistem Arayüzü"nü (System UI) seçin
4. Kaydettikten sonra, Hook'un etkili olması için ilgili etki alanındaki uygulamaları yeniden başlatmak üzere **uygulamanın sağ üst köşesindeki yeniden başlat düğmesine** tıklayın (veya telefonunuzu doğrudan yeniden başlatın).

---

### Adım 2: HyperCeiler'da Odak Bildirimi Beyaz Listesini Etkinleştirin

> 💡 Süper Ada stili bildirimlerin düzgün görüntülenmesi için HyperCeiler'ın "Odak Bildirimi" (Focus Notification) izninden geçmesi gerekir.  
> HyperCeiler sürümünüz çok eskiyse, ilgili ayarı bulamayabilirsiniz, lütfen sürümünüzü güncelleyin.

1. **HyperCeiler**'ı açın ve "Sistem Arayüzü" (System UI) veya "Xiaomi Hizmet Çerçevesi" (Xiaomi Services Framework) ile ilgili ayarlara gidin.
2. "**Odak Bildirimi Beyaz Listesini Kaldır**" (Remove Focus Notification Whitelist) seçeneğini bulun.
3. Anahtarı açın ve etkili olması için ilgili etki alanını yeniden başlatın.


---

## Şablon Açıklamaları

| Şablon | Açıklama |
|----------|-----------------------------------------|
| Bildirim Süper Ada | Herhangi bir bildirimi Odak Bildirimi + Süper Ada ekranına dönüştürmeyi destekler |
| Bildirim Süper Ada - Sade (Lite) | Süper Ada alanından tasarruf etmek için bildirimlerdeki "x yeni mesaj" ve yinelenen alanları otomatik olarak kaldırır |
| İndirme | İndirme durumunu otomatik olarak tanır ve Odak Bildirimi + Süper Ada'ya dönüştürür. Adanın sol tarafında durum gösterilirken, sağ tarafında dosya adı ve ilerleme çemberi gösterilir |
| İndirme - Sade (Lite) | Yukarıdaki ile aynı, ancak Süper Ada yalnızca simge + ilerleme çemberini gösterir |
| AI Bildirim Süper Ada | Süper Ada'nın sol ve sağ kısımları sadeleştirme için yapay zekaya bırakılarak içeriğin çok uzun olmaması sağlanır |

---
## ⚠️ Dikkat Edilmesi Gerekenler

| Madde | Açıklama |
|------|------|
| Çerçeve Bağımlılığı | Bu modül **LSPosed** çerçevesini kullanır ve Root erişimli bir cihaz gerektirir |
| Yeniden Başlatma Zamanı | Yeni bir uygulama yükledikten veya güncelledikten sonra etki alanını yeniden başlatmanız gerekir; yapılandırma değişiklikleri genellikle "hot reload" ile anında uygulanır |
| Bildirim Uyumluluğu | Genel uyumluluk yalnızca **standart Android bildirimlerini** işler, özel bildirim stilleri desteklenmez |
| ROM Uyumluluğu | Bu modül **HyperOS 3** ortamında test edilmiştir, diğer ROM'larda uyumluluk sorunları olabilir |

---

## 🔨 Derleme

Flutter geliştirme ortamının kurulu olduğundan emin olun, ardından komutu çalıştırın:

```bash
flutter build apk --target-platform=android-arm64
```

---

## 📄 Lisans

Bu proje [MIT Lisansı](LICENSE) altında açık kaynak kodludur, Issue ve PR'lar her zaman memnuniyetle karşılanır.

<div align="center">

HyperOS kullanıcıları için ❤️ ile yapılmıştır

[![Star History](https://img.shields.io/github/stars/1812z/HyperIsland?style=social)](https://github.com/1812z/HyperIsland)

</div>
