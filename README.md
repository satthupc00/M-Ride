# M Ride

App Android (tối ưu cho Galaxy Z Fold 7) ghi lại đường bạn đi xe mỗi ngày, vẽ trên bản đồ OpenStreetMap và phát lại hành trình vào cuối ngày.

- Tự bắt đầu ghi khi máy nhận ra bạn đang đi xe, tự dừng khi đứng yên.
- Màn **Hôm nay**: bản đồ + tổng quãng đường, thời gian, tốc độ.
- Màn **Hành trình**: danh sách theo ngày, phát lại từng chuyến hoặc cả ngày (1× → 120×).
- Lưu trên máy, đồng bộ lên mây khi đăng nhập Google.
- Tự kiểm tra và cài bản mới từ trang Releases.

## Cài đặt
Mở trang **Releases** của repo trên điện thoại, tải file `M-Ride-vX.Y.Z.apk` mới nhất và cài. Các bản sau app tự báo và tự cập nhật.

## Khoá ký app (GitHub Secrets)
Khoá ký app không nằm trong repo. GitHub Actions đọc nó từ 2 secret trong **Settings → Secrets and variables → Actions**:
- `KEYSTORE_BASE64`: file khoá dạng base64
- `KEYSTORE_PASSWORD`: mật khẩu khoá

Nếu thiếu 2 secret này, Actions chỉ build thử chứ không tạo bản phát hành.

## Thiết lập đồng bộ (làm một lần)
1. Vào https://console.firebase.google.com → **Add project** → đặt tên `M Ride` → tạo.
2. Trong project: **Build → Authentication → Get started → Sign-in method → Google → Enable** → Save.
3. **Build → Firestore Database → Create database** → chọn vùng `asia-southeast1` → **Start in production mode**.
   Tab **Rules**, dán rồi bấm Publish:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{db}/documents {
       match /users/{uid}/{doc=**} {
         allow read, write: if request.auth != null && request.auth.uid == uid;
       }
     }
   }
   ```
4. Bánh răng **Project settings → Your apps → biểu tượng Android**:
   - Package name: `com.mondiro.mride`
   - SHA-1: `AA:EF:13:D2:19:02:6B:B9:F2:C8:3F:72:BE:37:0C:58:9D:8D:CA:B3`
   - Bấm Register → tải file **google-services.json** → bỏ vào thư mục `app/` của repo (hoặc gửi cho Claude).
