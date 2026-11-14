# ADT Music

**Müziğin, tarzın, kontrol sende.**

ADT Music, YouTube Music kataloğuna erişen, kendine özgü **Minimal** tasarım diliyle yazılmış, reklamsız ve gizlilik odaklı bir Android müzik uygulamasıdır. Material bileşenleri kullanılmaz: kırık beyaz kâğıt, mürekkep siyahı, keskin köşeler, büyük başlıklar ve çalan şarkının kapağından gelen tek bir vurgu rengi.

## Özellikler

**Keşif**
- YouTube Music'te şarkı, albüm, sanatçı, çalma listesi ve video arama (öneriler, sonsuz kaydırma, "bunu mu demek istedin")
- Ana sayfa: Senin için (son dinlediğine göre), Türkiye trendleri, öne çıkan listeler, albümler, tekrar dinle
- 14 ruh hali / tür sayfası (Türkçe Pop, Rap, Anadolu Rock, Lo-fi, Odak…)
- Sanatçı sayfası, albüm ve liste sayfaları
- YouTube / YouTube Music bağlantısını yapıştır, paylaş ya da "ADT Music ile aç": anında çalar

**Dinleme**
- Arka planda çalma; bildirim, kilit ekranı, kulaklık ve Bluetooth kontrolleri
- Bildirimde beğen düğmesi
- Otomatik radyo: sıra bitince benzer şarkılarla devam eder
- Sıra yönetimi (sıradaki çal, sıraya ekle, taşı, çıkar, temizle), karıştır, tekrarla
- Kaldığın yerden devam
- Hız ve ton ayarı: şarkıyı yavaşlat/hızlandır (0.5x – 2x), tonu ±12 yarım ton değiştir; Slowed, Daycore, Nightcore ön ayarları
- Sessiz kısımları atlama
- Android Auto desteği (beğenilenler, son dinlenenler, indirilenler, listeler, trendler, sesli arama)

**Görünüm**
- Dinamik vurgu rengi: çalan şarkının kapağından çıkarılır, akıcı geçişle değişir
- Açık / koyu / sistem teması
- Kapağı sağa-sola kaydırarak şarkı değiştirme, çift dokunarak beğenme
- Oynatıcıyı aşağı çekerek küçültme

**Sözler**
- LRCLIB'den zaman damgalı (senkron) sözler
- Çalan satır soldan sağa dolan bir vurguyla ilerler, otomatik kayar
- Satıra dokununca şarkı o ana atlar
- Senkron söz yoksa düz metin

**Çevrimdışı**
- Şarkı, albüm, liste ya da tüm beğenilenleri indirme
- Akıllı önbellek: dinlediğin şarkılar saklanır, yer dolunca en eski dinlenen silinir (512 MB – 4 GB)
- Yalnızca Wi-Fi ile indirme seçeneği

**Kütüphane**
- Beğenilenler, kendi çalma listelerin (oluştur, yeniden adlandır, sil), albümleri kütüphaneye kaydetme
- Gün gün dinleme geçmişi
- Dinleme istatistikleri: toplam süre, en çok dinlenen şarkılar ve sanatçılar

**Ses**
- 10 ön ayarlı ekolayzer + bant bant ayar
- Bas güçlendirme, sanal surround
- Uyku zamanlayıcısı (sesi yavaşça kısarak durdurur; "şarkı bitince" seçeneği)

**Gizlilik**
- Takipçi, analiz, telemetri yok. Hesap gerekmez.
- Tüm veriler (beğeniler, listeler, geçmiş, indirmeler) yalnızca cihazda.
- Arama geçmişini kapatma, dinleme geçmişini duraklatma

## APK nasıl derlenir?

Bilgisayara bir şey kurman gerekmez; APK'yı GitHub derler.

1. GitHub'da yeni bir depo aç (ör. `adt-music`).
2. Bu klasördeki **her şeyi** (gizli `.github` klasörü dahil) depoya yükle.
3. **Actions** sekmesinde "APK derle" işi kendiliğinden başlar (yaklaşık 8–12 dakika).
4. Bitince APK iki yerde olur:
   - **Releases** bölümünde `ADT Music build N` (doğrudan telefondan indirilebilir)
   - İşin sayfasında **Artifacts → ADT-Music-apk**
5. Telefonda APK'yı aç, "bilinmeyen kaynaklara izin ver" de, kur.

Derleme hata verirse Actions sayfasındaki kırmızı adımı aç, hata satırlarını kopyalayıp Claude'a gönder.

### İmza anahtarı

`app/adtmusic.jks`, güncellemelerin eskisinin üstüne kurulabilmesi için depoda duran sabit bir anahtardır. Uygulamayı başkalarıyla paylaşacaksan kendi anahtarını oluştur ve GitHub Secrets ile ver (`ADT_KEYSTORE_PATH`, `ADT_KEYSTORE_PASSWORD`, `ADT_KEY_ALIAS`, `ADT_KEY_PASSWORD`).

### YouTube değişince

YouTube sayfa yapısını zaman zaman değiştirir; o zaman şarkılar açılmayabilir. Çözüm genelde NewPipeExtractor'ı güncellemektir: `app/build.gradle.kts` içindeki `NewPipeExtractor:v0.27.1` satırını [en yeni sürümle](https://github.com/TeamNewPipe/NewPipeExtractor/releases) değiştir ve tekrar derle.

## Teknoloji

Kotlin · Jetpack Compose (Material yok, tüm bileşenler özel) · Media3 / ExoPlayer · Room · Coil · Palette · NewPipeExtractor · LRCLIB · OkHttp

```
app/src/main/java/com/alideniz/adtmusic/
├── data/        YouTube Music, sözler, veritabanı, ayarlar
├── playback/    çalma servisi, önbellek, indirmeler, ekolayzer, uyku zamanlayıcısı
└── ui/          tema, simgeler, bileşenler, ekranlar
```

## Yasal uyarı

Bu proje YouTube, Google LLC veya bağlı kuruluşlarıyla ilişkili değildir ve onlar tarafından onaylanmamıştır. Resmî bir API kullanmaz; eğitim ve kişisel kullanım amacıyla geliştirilmiştir. YouTube'un kullanım şartlarına aykırı olabileceğinden Play Store'da yayınlanması uygun değildir.

## Lisans

GPL-3.0 (NewPipeExtractor GPL-3.0 lisanslı olduğu için). Archivo yazı tipi SIL Open Font License ile dağıtılır (`FONT_LICENSE_OFL.txt`).
