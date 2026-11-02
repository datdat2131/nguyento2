# NguyenTo 1.3 - Hệ thống nguyên tố cho Minecraft (Paper/Spigot 1.20.4 - 1.21.x)

## Build
Cần JDK 17+ và Maven:  `mvn clean package`  ->  `target/NguyenTo.jar` bỏ vào `plugins/`.
(Hoặc đẩy lên GitHub, tab Actions sẽ tự build - xem .github/workflows/build.yml)

## Nguyên tố
Lửa, Nước, Đất, Gió, Sét, Băng + 3 nguyên tố HUYỀN THOẠI: Bóng Tối, Ánh Sáng, Rồng.
Mọi buff / nội tại / chiêu chỉnh trong `plugins/NguyenTo/config.yml`, sau đó gõ `/nguyento reload`.
Mô tả dưới mỗi nguyên tố trong GUI tự cập nhật theo config.

## Fusion (hợp thể)
Kết hợp 2 nguyên tố thành 1, chỉ chiếm 1 ô trang bị nhưng nhận buff + nội tại + chiêu của cả hai + buff riêng:
- Lửa + Sét = **Plasma** (Bùng Nổ Plasma)
- Nước + Băng = **Băng Thủy** (Đóng Băng)
- Ánh Sáng + Rồng = **Thánh Long** (Phán Quyết, có chiêu Thần Phạt)
- Bóng Tối + Rồng = **Hắc Long** (Lời Nguyền, có chiêu Lãnh Địa)
- Lửa + Đất = **Dung Nham** (Phun Trào)
- Gió + Sét = **Bão Tố** (Cơn Bão Sét, hất tung)
- Gió + Băng = **Bão Tuyết** (làm chậm cả vùng)
- Lửa + Rồng = **Viêm Long** (Long Viêm Thiêu Đốt)

Cách dùng: sở hữu đủ 2 nguyên tố gốc -> `/nguyento` -> click hợp thể ở hàng dưới (hoặc `/nguyento fuse <id>`).
Nguyên tố gốc không bị mất. Không thể trang bị hợp thể cùng nguyên tố gốc của nó.
Chỉnh phí (`fusion.cost-levels`) và độ mạnh (`fusion.inherit-stats-percent`) trong config.yml.

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
- /nguyento fuse <id>                  : kết hợp thành hợp thể (plasma, bang_thuy, thanh_long, hac_long, dung_nham, bao_to, bao_tuyet, viem_long)
- /nguyento reset [người]              : bỏ trang bị + xóa buff kẹt (admin)
- /nguyento reload                     : tải lại config (admin)
ID: lua, nuoc, dat, gio, set, bang, bong_toi, anh_sang, rong, plasma, bang_thuy, thanh_long, hac_long, dung_nham, bao_to, bao_tuyet, viem_long

## Hiệu năng
Mục `performance:` trong config.yml: `orbit-update-ticks` (tăng lên 3-4 nếu server yếu), `particles`
(false = tắt hạt), `particle-density`, `view-range-blocks`. Sửa xong gõ /nguyento reload.

## Thoát game
Người chơi thoát game sẽ tự động bỏ trang bị toàn bộ nguyên tố (buff reset, vật phẩm kỹ năng bị thu hồi).
Vào lại phải /nguyento để trang bị lại. Nguyên tố đã sở hữu thì được giữ.
