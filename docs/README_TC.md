# 📬 請求速率限制服務 — 後端工程師作業

這是提供給後端工程師應徵者的技術作業。請使用 **Spring Boot** 建立 RESTful 請求速率限制服務，並整合 **MySQL**、**Redis** 與 **RocketMQ**。

---

## 🎯 作業目標

實作簡單的 API 請求速率限制服務，依使用者或 API 金鑰追蹤使用量，並阻擋超過允許上限的請求。

---

## 🔧 待實作功能

### 1️⃣ 設定請求速率限制

**端點：** `POST /limits`

```json
{
  "apiKey": "abc-123",
  "limit": 100,
  "windowSeconds": 60
}
```

**預期行為：**

- 設定指定 API 金鑰在一段時間視窗內的請求次數上限
- 將此設定儲存至 MySQL

---

### 2️⃣ 檢查 API 存取權限

**端點：** `GET /check?apiKey=abc-123`

**預期行為：**

- 增加該 API 金鑰的使用次數
- 若使用次數超過上限，回傳表示請求遭阻擋的回應
- 使用 Redis（INCR + EXPIRE）追蹤時間視窗內的使用量

---

### 3️⃣ 查詢使用量

**端點：** `GET /usage?apiKey=abc-123`

**預期行為：**

- 回傳目前使用次數、剩餘配額與時間視窗的剩餘存活時間（TTL）

---

### 4️⃣ 刪除限制規則

**端點：** `DELETE /limits/{apiKey}`

**預期行為：**

- 從 MySQL 刪除請求速率限制設定
- 清除 Redis 中的相關資料

---

### 5️⃣ 查看所有限制規則

**端點：** `GET /limits`

**預期行為：**

- 列出所有有效的 API 金鑰及其對應的限制設定
- 支援分頁

---

## 🧪 加分項目（選做）

- 使用 Spring Cache 抽象層，或封裝 RedisTemplate 操作
- 妥善處理錯誤，並回傳具有明確意義的狀態碼
- 自行定義 DTO 與 RocketMQ 訊息格式
- 採用一致且模組化的程式碼結構（controller、service、repository、config 等）
- 盡可能提高測試案例的涵蓋範圍

---

## 🐳 環境設定

使用專案提供的 `docker-compose.yaml` 啟動所需服務：

| 服務 | 連接埠 |
|---|---|
| MySQL | 3306 |
| Redis | 6379 |
| RocketMQ Namesrv | 9876 |
| RocketMQ Broker | 10911 |
| RocketMQ Console | 8088 |

啟動服務：

```commandline
docker compose up -d
```

MySQL 連線資訊：

- 使用者：`taskuser`
- 密碼：`taskpass`
- 資料庫：`taskdb`

可編輯 `init.sql`，在資料庫初始化時自動建立所需資料表。

---

## 🚀 快速開始

啟動應用程式：

```bash
./mvnw spring-boot:run
```

請確認 `src/main/resources/application.yaml` 中的下列連線設定正確：

- spring.datasource.url
- spring.data.redis.host
- rocketmq.name-server

Makefile 操作、JAR 打包與環境啟動說明請參考 [HELP.md](../HELP.md)。

## ✅ 實作狀態

README 要求的五個 API 均已實作，包含 MySQL 規則持久化、Spring Cache 規則快取、Redis 原子計數與 TTL、分頁、統一錯誤回應，以及 RocketMQ 首次超額事件的發送、消費與冪等稽核紀錄。詳細操作與 curl 範例請參考 [HELP.md](../HELP.md)。

---

## 📤 繳交方式

請提交一個公開的 GitHub 儲存庫，包含以下內容：

- ✅ 完整且可執行的原始碼
- ✅ README.md（本文件）
- ✅ 請在 HELP.md 中補充必要的環境設定與資料腳本
- ✅ 選填：Postman 集合或 curl 範例  

---

## 📌 注意事項

- 著重 API 正確性、基本錯誤處理，以及各項技術的適當使用
- 可以使用 Vibe Coding／ChatGPT 等方式或工具輔助，但請自行撰寫並理解自己的程式碼
- 預計完成時間約為 3 小時

祝你順利完成！

---
