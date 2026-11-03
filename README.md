# NguyenTo 1.4 - Hệ thống nguyên tố cho Minecraft (Paper/Spigot 1.20.4 - 1.21.x)

## Build
Cần JDK 17+ và Maven:  `mvn clean package`  ->  `target/NguyenTo.jar` bỏ vào `plugins/`.
(Hoặc đẩy lên GitHub, tab Actions sẽ tự build - xem .github/workflows/build.yml)

## Nguyên tố
Lửa, Nước, Đất, Gió, Sét, Băng + 3 nguyên tố HUYỀN THOẠI: Bóng Tối, Ánh Sáng, Rồng.
Mọi buff / nội tại / chiêu chỉnh trong `plugins/NguyenTo/config.yml`, sau đó gõ `/nguyento reload`.
Mô tả dưới mỗi nguyên tố trong GUI tự cập nhật theo config.

## Fusion (hợp thể)
Kết hợp 2 nguyên tố thành 1 **hợp thể**: một nguyên tố độc lập (chỉ chiếm 1 ô) với buff + nội tại riêng.
**Kết hợp sẽ làm bạn MẤT cả hai nguyên tố gốc** (muốn có lại phải lĩnh ngộ bằng cuộn giấy).

Cách dùng: `/nguyento` -> nút Đe ở giữa (hoặc `/nguyento fusion`) -> click hợp thể 2 lần để xác nhận.
Menu hợp thể cũng dùng để trang bị / tắt hợp thể đã sở hữu.

| Công thức | Hợp thể | Nội tại |
|---|---|---|
| Lửa + Nước | **Fire Water** | 15% vụ nổ hơi nước: sát thương phép lan, Giảm Tốc I, +30% nếu Thiêu Đốt, 10% Choáng nếu Ướt. Máu <20%: màn sương -20% sát thương 3s |
| Đất + Gió | **Bão Cát** | 15% bão cát 4 block: giảm tốc 30%, 10% hất tung, trúng ≥3 mục tiêu +15% tốc độ. Máu <30%: Khiên Cát -20% sát thương 3s |
| Sét + Băng | **Hàn Lôi** | 15% tia sét băng: sát thương sét + băng, Chậm 35%, mục tiêu đang chậm 25% Đóng Băng 0,8s, đánh mục tiêu đóng băng phá băng +40%. Máu <25%: Lôi Băng Hộ Thể -20% 4s (hồi 30s) |
| Lửa + Sét | **Lôi Viêm** | 15% Thiên Hỏa Lôi Bạo: Thiêu Đốt 3s + Nhiễm Điện 2s; có cả hai: Bộc Phát +50%, lan 3 kẻ địch trong 4 block (hồi 5s) |
| Ánh Sáng + Rồng | **Thánh Long** | 15% Long Hồn Ánh Sáng + Thánh Ấn 5s (+35% sát thương). Máu <30%: Long Hộ -25% sát thương, +15% tốc độ 5s (hồi 45s) |
| Bóng Tối + Rồng | **Hắc Long** | 15% Hắc Long Hồn + Hắc Ấn 5s: Thôn Phệ +40% sát thương, hồi 5% máu. Máu <25%: Hắc Long Hộ Thể -30% sát thương, +20% sát thương 5s (hồi 45s) |
| Nước + Băng | **Băng Thủy** | 12% đóng băng mục tiêu 2s |
| Lửa + Đất | **Dung Nham** | 12% phun trào, đốt |
| Gió + Sét | **Bão Tố** | 14% sét lan, hất tung |
| Gió + Băng | **Bão Tuyết** | 12% làm chậm cả vùng |
| Lửa + Rồng | **Viêm Long** | 12% lửa rồng lan, đốt |

Trạng thái dùng trong nội tại: **Thiêu Đốt** = đang cháy; **Ướt** = đang trong nước, đang bị mưa dội hoặc vừa dính hơi nước Fire Water;
**Nhiễm Điện / Đóng Băng / Thánh Ấn / Hắc Ấn** là trạng thái có thời hạn do chính các hợp thể gây ra.
Toàn bộ con số chỉnh trong `config.yml` (mục `elements.<id>`), phí kết hợp ở `fusion.cost-levels`.

## Dùng chiêu (vật phẩm kỹ năng)
Mọi buff / nội tại hoạt động ở cả 3 ô. Bóng Tối / Ánh Sáng / Rồng trang bị ở ô nào cũng được
nhận 1 vật phẩm kỹ năng (trang bị nhiều nguyên tố có chiêu thì nhận nhiều vật phẩm). Chuột phải để dùng chiêu.
Cả 3 chiêu đều hồi 90 giây:
- **Ánh Sáng - Thần Phạt**: 3 cột ánh sáng giáng xuống 3 kẻ địch gần nhất, mỗi cột 35 sát thương (bán kính 3 block).
- **Bóng Tối - Lãnh Địa**: lãnh địa 10 giây, kẻ địch bên trong bị KHÓA CHÂN, không chạy ra ngoài được (kể cả ngọc ender / chorus).
- **Rồng - Hơi Thở Rồng**: vùng hơi thở bán kính 20 block, 10 sát thương mỗi giây trong 10 giây.
Vật phẩm bất ly thân: không vứt được, không cất vào rương/ender chest/bàn chế tạo/bundle/
khung vật phẩm, người khác không nhặt hay dùng được, chết không rơi ra.
Gỡ nguyên tố thì vật phẩm tự biến mất.

## Lệnh
- /nguyento (/nt)                      : mở GUI
- /nguyento give <người> <id|all> [n]  : đưa cuộn giấy (admin)
- /nguyento unlock <người> <id|all>    : mở khóa trực tiếp (admin)
- /nguyento stats                      : xem chỉ số buff thật đang có
- /nguyento fusion                     : mở menu Hợp Thể
- /nguyento reset [người]              : bỏ trang bị + xóa buff kẹt (admin)
- /nguyento reload                     : tải lại config (admin)
ID: lua, nuoc, dat, gio, set, bang, bong_toi, anh_sang, rong, fire_water, bao_cat, han_loi, loi_viem, bang_thuy, thanh_long, hac_long, dung_nham, bao_to, bao_tuyet, viem_long

## Hiệu năng
Mục `performance:` trong config.yml: `orbit-update-ticks` (tăng lên 3-4 nếu server yếu), `particles`
(false = tắt hạt), `particle-density`, `view-range-blocks`. Sửa xong gõ /nguyento reload.

## Thoát game
Người chơi thoát game sẽ tự động bỏ trang bị toàn bộ nguyên tố (buff reset, vật phẩm kỹ năng bị thu hồi).
Vào lại phải /nguyento để trang bị lại. Nguyên tố đã sở hữu thì được giữ.
