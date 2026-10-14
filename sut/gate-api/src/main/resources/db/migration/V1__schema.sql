-- Gate Appointment schema.
CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('DRIVER', 'CLERK')),
    display_name  VARCHAR(100) NOT NULL,
    truck_plate   VARCHAR(20),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- A slot is a time window at one gate lane with a fixed number of places.
CREATE TABLE slot (
    id         BIGSERIAL PRIMARY KEY,
    lane       VARCHAR(12) NOT NULL,
    start_at   TIMESTAMPTZ NOT NULL,
    end_at     TIMESTAMPTZ NOT NULL,
    capacity   INTEGER     NOT NULL CHECK (capacity BETWEEN 1 AND 50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT slot_window_valid CHECK (end_at > start_at),
    CONSTRAINT slot_lane_start_unique UNIQUE (lane, start_at)
);
CREATE INDEX ix_slot_start ON slot (start_at);

CREATE TABLE booking (
    id               BIGSERIAL PRIMARY KEY,
    reference        VARCHAR(11)  NOT NULL UNIQUE,
    driver_id        BIGINT       NOT NULL REFERENCES app_user (id),
    slot_id          BIGINT       NOT NULL REFERENCES slot (id),
    container_number VARCHAR(11)  NOT NULL,
    direction        VARCHAR(10)  NOT NULL CHECK (direction IN ('DROP_OFF', 'PICK_UP')),
    status           VARCHAR(12)  NOT NULL CHECK (status IN ('BOOKED', 'CANCELLED', 'CHECKED_IN')),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    checked_in_at    TIMESTAMPTZ,
    checked_in_by    BIGINT REFERENCES app_user (id)
);
CREATE INDEX ix_booking_slot ON booking (slot_id, status);
CREATE INDEX ix_booking_driver ON booking (driver_id);
-- A container can have at most one open (not yet used or cancelled) appointment.
CREATE UNIQUE INDEX ux_booking_open_container ON booking (container_number) WHERE status = 'BOOKED';
