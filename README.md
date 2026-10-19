# NguyenTo 1.1 - Hệ thống nguyên tố cho Minecraft (Paper/Spigot 1.20.4 - 1.21.x)

## Build
Cần JDK 17+ và Maven:  `mvn clean package`  ->  `target/NguyenTo.jar` bỏ vào `plugins/`.
(Hoặc đẩy lên GitHub, tab Actions sẽ tự build - xem .github/workflows/build.yml)

## Nguyên tố
Lửa, Nước, Đất, Gió, Sét, Băng + 2 nguyên tố HUYỀN THOẠI: Bóng Tối, Ánh Sáng.
Mọi buff / nội tại / chiêu chỉnh trong `plugins/NguyenTo/config.yml`, sau đó gõ `/nguyento reload`.
Mô tả dưới mỗi nguyên tố trong GUI tự cập nhật theo config.

## Dùng chiêu (vật phẩm kỹ năng)
Mọi buff / nội tại hoạt động ở cả 3 ô. Bóng Tối / Ánh Sáng trang bị ở ô nào cũng được
nhận 1 vật phẩm kỹ năng (trang bị cả hai thì nhận hai vật phẩm). Chuột phải để dùng chiêu.
Vật phẩm bất ly thân: không vứt được, không cất vào rương/ender chest/bàn chế tạo/bundle/
khung vật phẩm, người khác không nhặt hay dùng được, chết không rơi ra.
Gỡ nguyên tố thì vật phẩm tự biến mất.

## Lệnh
- /nguyento (/nt)                      : mở GUI
- /nguyento give <người> <id|all> [n]  : đưa cuộn giấy (admin)
- /nguyento unlock <người> <id|all>    : mở khóa trực tiếp (admin)
- /nguyento stats                      : xem chỉ số buff thật đang có
- /nguyento reload                     : tải lại config (admin)
ID: lua, nuoc, dat, gio, set, bang, bong_toi, anh_sang

## Hiệu năng
Mục `performance:` trong config.yml: `orbit-update-ticks` (tăng lên 3-4 nếu server yếu), `particles`
(false = tắt hạt), `particle-density`, `view-range-blocks`. Sửa xong gõ /nguyento reload.
