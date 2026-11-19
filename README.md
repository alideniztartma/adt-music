<div align="center">

<img src=".github/assets/banner.svg" alt="ADT Music" width="100%"/>

**Reklamsız · Hesapsız · Takipçisiz** YouTube Music istemcisi — Android, Windows ve Linux.

[![Sürüm](https://img.shields.io/badge/sürüm-1.5-FF5A1F?style=flat-square&labelColor=111111)](https://github.com/alideniztartma/adt-music/releases)
[![APK derle](https://img.shields.io/github/actions/workflow/status/alideniztartma/adt-music/build.yml?style=flat-square&label=Android&labelColor=111111)](https://github.com/alideniztartma/adt-music/actions/workflows/build.yml)
[![Masaüstü derle](https://img.shields.io/github/actions/workflow/status/alideniztartma/adt-music/desktop.yml?style=flat-square&label=Masaüstü&labelColor=111111)](https://github.com/alideniztartma/adt-music/actions/workflows/desktop.yml)
[![Lisans](https://img.shields.io/badge/lisans-GPL--3.0-F4F2EE?style=flat-square&labelColor=111111)](LICENSE)

</div>

## Ekran görüntüleri

| Ana sayfa | Oynatıcı |
|---|---|
| <img src=".github/assets/desktop/home.png" alt="Ana sayfa"/> | <img src=".github/assets/desktop/player.png" alt="Oynatıcı"/> |
| **Kütüphane** | **Koyu tema** |
| <img src=".github/assets/desktop/library.png" alt="Kütüphane"/> | <img src=".github/assets/desktop/dark.png" alt="Koyu tema"/> |

<sub>Masaüstü sürümünden, örnek verilerle. Android'de arayüz aynıdır.</sub>

## İndir

[**Releases**](https://github.com/alideniztartma/adt-music/releases) sayfasından:

| Sistem | Dosya | Not |
|---|---|---|
| Android 8+ | `ADT Music build N` → `.apk` | Güncellemeler uygulamanın içinden gelir |
| Windows 10/11 | `ADT Music masaüstü N` → `.msi` | Kurulumsuz: `windows-portable.zip` |
| Ubuntu / Debian / Mint | `.deb` → `sudo apt install ./adt-music_*.deb` | Ses için: `sudo apt install vlc` |
| Fedora / diğer Linux | `.rpm` ya da `linux-x64.tar.gz` | Ses için VLC gerekir |

## Neler var

- YouTube Music'te şarkı, albüm, sanatçı ve liste arama; trendler, ruh hali sayfaları, otomatik radyo
- Zaman damgalı canlı sözler ve paylaşılabilir söz kartları
- Hız ve ton ayarı, ekolayzer, ses yükseltme, uyku zamanlayıcısı
- İndirme ve internetsiz dinleme, çalma listeleri, beğeniler, geçmiş
- Günlük karışımlar, ADT Wrapped, istatistikler, rozetler, Mixtape
- Şarkıya anı bırakma ve zaman kapsülü
- Kapaktan renk alan tema, koyu mod, köşe ve yazı boyutu seçenekleri
- Yedekleme / geri yükleme — bütün veriler yalnızca senin cihazında

Yalnızca Android'de: bildirim ve kilit ekranı kontrolleri, Android Auto, ses görselleştirici, Bluetooth ile otomatik çalma.<br/>
Masaüstü kısayolları: **Esc** geri · **Ctrl+Boşluk** çal/duraklat · **Ctrl+←/→** önceki/sonraki · medya tuşları.

## Kendin derle

Bilgisayarına bir şey kurman gerekmez; GitHub Actions derler.

1. Depoyu fork'la.
2. **Actions** sekmesinde *APK derle* (Android) ve *Masaüstü derle* (Windows + Linux) işleri kendiliğinden çalışır.
3. Dosyalar **Releases** sayfasına düşer.

Android kodu `app/`, masaüstü kodu `desktop/` klasöründe. Şarkılar açılmazsa genelde çözüm, iki `build.gradle.kts` dosyasındaki `NewPipeExtractor` sürümünü [en yenisiyle](https://github.com/TeamNewPipe/NewPipeExtractor/releases) değiştirmektir.

## Yasal uyarı

Bu proje YouTube veya Google LLC ile ilişkili değildir. Eğitim ve kişisel kullanım amaçlıdır; YouTube verilerine [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) ile erişilir.

## Lisans

[GPL-3.0](LICENSE) · Yazı tipi: Archivo ([OFL](FONT_LICENSE_OFL.txt))
