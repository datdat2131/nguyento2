# NguyenTo - Hệ thống nguyên tố cho Minecraft (Paper/Spigot 1.20.4+)

## Build
Cần JDK 17+ và Maven:

    mvn clean package

File plugin nằm ở `target/NguyenTo.jar` -> bỏ vào thư mục `plugins/` của server.

## Lệnh
- `/nguyento` (hoặc `/nt`) : mở GUI trang bị / tắt nguyên tố
- `/nguyento give <người chơi> <lua|nuoc|dat|gio|set|bang|all> [số lượng]` : đưa cuộn giấy (quyền nguyento.admin)
- `/nguyento unlock <người chơi> <nguyên tố|all>` : mở khóa trực tiếp (quyền nguyento.admin)

## Cách chơi
- Chuột phải cuộn giấy để sở hữu nguyên tố (dùng một lần, trùng thì không mất cuộn).
- Trang bị tối đa 3 nguyên tố: Ô 1 bay trên đầu, Ô 2 vai trái, Ô 3 vai phải (nghiêng ra ngoài).
