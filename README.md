# NguyenTo 1.1 - Hệ thống nguyên tố cho Minecraft (Paper/Spigot 1.20.4 - 1.21.x)

## Build
Cần JDK 17+ và Maven:  `mvn clean package`  ->  `target/NguyenTo.jar` bỏ vào `plugins/`.
(Hoặc đẩy lên GitHub, tab Actions sẽ tự build - xem .github/workflows/build.yml)

## Nguyên tố
Lửa, Nước, Đất, Gió, Sét, Băng + 2 nguyên tố HUYỀN THOẠI: Bóng Tối, Ánh Sáng.
Mọi buff / nội tại / chiêu chỉnh trong `plugins/NguyenTo/config.yml`, sau đó gõ `/nguyento reload`.
Mô tả dưới mỗi nguyên tố trong GUI tự cập nhật theo config.

## Dùng chiêu (vật phẩm kỹ năng)
Khi Bóng Tối / Ánh Sáng nằm ở Ô số 1, người chơi tự nhận 1 vật phẩm kỹ năng.
Chuột phải vật phẩm để dùng chiêu. Vật phẩm bất ly thân: không vứt được, không cất vào
rương/ender chest/bàn chế tạo/bundle/khung vật phẩm, người khác không nhặt hay dùng được,
chết không rơi ra. Gỡ nguyên tố khỏi Ô 1 thì vật phẩm tự biến mất.

## Lệnh
- /nguyento (/nt)                      : mở GUI
- /nguyento give <người> <id|all> [n]  : đưa cuộn giấy (admin)
- /nguyento unlock <người> <id|all>    : mở khóa trực tiếp (admin)
- /nguyento reload                     : tải lại config (admin)
ID: lua, nuoc, dat, gio, set, bang, bong_toi, anh_sang
