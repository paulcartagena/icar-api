# ICAR API — ERD

Modelo de datos inicial por módulo. Ver [Notas](./Notas.md) para decisiones de diseño y contexto. Este es un punto
de partida — ajusta campos/relaciones a medida que lo implementes, no es definitivo.

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#eef1f5', 'primaryBorderColor': '#94a3b8', 'primaryTextColor': '#1e293b', 'lineColor': '#94a3b8', 'attributeBackgroundColorOdd': '#ffffff', 'attributeBackgroundColorEven': '#eef1f5', 'fontSize': '14px'}}}%%
erDiagram
    %% --- Cluster financiero (todas conectadas entre sí) ---
    USER {
        uuid id PK
        string username
        string password_hash
        string full_name
        enum role "ADMIN | TREASURER"
        boolean enabled
    }

    DONATION {
        uuid id PK
        string donor_name
        string message
        decimal amount
        string currency
        enum method "CASH | PAYPAL | TRANSFER"
        enum status "PENDING | COMPLETED | FAILED"
        string paypal_order_id
        string paypal_capture_id
        string payer_email
        datetime created_at
        datetime completed_at
    }

    CATEGORY {
        uuid id PK
        string name
        enum type "INCOME | EXPENSE"
        boolean active
    }

    INCOME {
        uuid id PK
        uuid category_id FK
        uuid source_donation_id FK
        decimal amount
        string description
        date date
        uuid registered_by FK
    }

    EXPENSE {
        uuid id PK
        uuid category_id FK
        decimal amount
        string description
        date date
        string receipt_url
        uuid registered_by FK
    }

    BUDGET {
        uuid id PK
        uuid category_id FK
        date period_start
        date period_end
        decimal planned_amount
        uuid created_by FK
    }

    %% --- Cluster de membresía (conectadas entre sí) ---
    MEMBER {
        uuid id PK
        string first_name
        string last_name
        string email
        string phone
        date join_date
        enum status
        uuid family_id FK
    }

    FAMILY {
        uuid id PK
        string family_name
        string notes
        uuid responsible_member_id FK
    }

    MINISTRY {
        uuid id PK
        string name
        string description
    }

    SERVICE_SCHEDULE {
        uuid id PK
        uuid ministry_id FK
        string day_of_week
        time start_time
    }

    %% --- Cluster de eventos (conectadas entre sí) ---
    EVENT {
        uuid id PK
        string title
        text description
        datetime start_datetime
        datetime end_datetime
        string location
        boolean active
    }

    EVENT_PHOTO {
        uuid id PK
        uuid event_id FK
        string image_url
        int display_order
    }

    %% --- Sin relaciones: contenido singleton del sitio público ---
    ABOUT_US {
        uuid id PK
        string title
        text body
        string image_url
    }

    CONTACT_INFO {
        uuid id PK
        string address
        string phone
        string email
        string map_url
    }

    DONATION ||--o| INCOME : "genera al completarse"
    CATEGORY ||--o{ INCOME : "clasifica"
    CATEGORY ||--o{ EXPENSE : "clasifica"
    CATEGORY ||--o{ BUDGET : "clasifica"
    USER ||--o{ INCOME : "registra"
    USER ||--o{ EXPENSE : "registra"
    USER ||--o{ BUDGET : "crea"
    FAMILY ||--o{ MEMBER : "tiene"
    MEMBER ||--o{ FAMILY : "es responsable de"
    MEMBER }o--o{ MINISTRY : "participa en"
    MINISTRY ||--o{ SERVICE_SCHEDULE : "organiza"
    EVENT ||--o{ EVENT_PHOTO : "tiene"
```
