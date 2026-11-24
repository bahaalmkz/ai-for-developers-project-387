import "@typespec/http";
import "@typespec/openapi";
import "@typespec/openapi3";

using TypeSpec.Http;
using TypeSpec.OpenAPI;

@service(#{ title: "Календарь звонков API" })
@server("http://localhost:8080", "Локальный сервер разработки")
namespace CallCalendar;

model HealthStatus {
  status: "ok";
}

@doc("Тип встречи: предложение владельца.")
model EventType {
  @doc("Идентификатор: латиница в нижнем регистре, цифры, дефис.")
  id: string;

  title: string;
  description: string;

  @doc("Длительность в минутах, кратна 30, от 30 до 480.")
  durationMinutes: int32;
}

@doc("Свободный слот: интервал времени в поясе владельца (+05:00).")
model Slot {
  start: offsetDateTime;
  end: offsetDateTime;
}

@doc("Ссылка на тип встречи внутри встречи: id и название.")
model EventTypeRef {
  id: string;
  title: string;
}

@doc("Встреча: подтверждённая запись гостя на слот. Конец считает сервер.")
model Booking {
  id: string;
  eventType: EventTypeRef;
  start: offsetDateTime;
  end: offsetDateTime;
  guestName: string;
  guestEmail: string;

  @doc("Тема разговора от гостя; пустая строка — темы нет.")
  @maxLength(200)
  guestTopic: string;
}

@doc("Данные для записи гостя на слот.")
model CreateBookingRequest {
  eventTypeId: string;

  @doc("Начало слота (ISO 8601 со смещением), на сетке 30 минут в окне записи.")
  start: offsetDateTime;

  guestName: string;
  guestEmail: string;

  @doc("Необязательная тема разговора в одну строку, до 200 символов. Пробелы по краям обрезаются; пустая или из одних пробелов — темы нет.")
  guestTopic?: string;
}

@doc("Единый формат ошибки.")
model ApiError {
  code: string;
  message: string;

  @doc("Для ошибок данных: поле → сообщение.")
  fieldErrors?: Record<string>;
}

@doc("Данные для создания типа встречи.")
model CreateEventTypeRequest {
  id: string;
  title: string;

  @doc("Необязательно: если не указано, сохраняется пустое описание.")
  description?: string;

  durationMinutes: int32;
}

@route("/api")
namespace Api {
  @get
  @route("/health")
  @tag("Health")
  @operationId("getHealth")
  op getHealth(): HealthStatus;

  @get
  @route("/event-types")
  @tag("EventTypes")
  @operationId("listEventTypes")
  op listEventTypes(): EventType[] | {
    @statusCode _: 500;
    @body body: ApiError;
  };

  @post
  @route("/event-types")
  @tag("EventTypes")
  @operationId("createEventType")
  op createEventType(@body body: CreateEventTypeRequest):
    | {
        @statusCode _: 201;
        @body body: EventType;
      }
    | {
        @statusCode _: 400;
        @body body: ApiError;
      }
    | {
        @statusCode _: 409;
        @body body: ApiError;
      }
    | {
        @statusCode _: 500;
        @body body: ApiError;
      };

  @get
  @route("/event-types/{id}/slots")
  @tag("Slots")
  @operationId("listSlots")
  @doc("Свободные слоты типа встречи на окно записи (14 дней), по возрастанию старта.")
  op listSlots(@path id: string): Slot[] | {
    @statusCode _: 404;
    @body body: ApiError;
  } | {
    @statusCode _: 500;
    @body body: ApiError;
  };

  @get
  @route("/bookings")
  @tag("Bookings")
  @operationId("listBookings")
  @doc("Предстоящие встречи всех типов (конец позже текущего момента), по возрастанию начала.")
  op listBookings(): Booking[] | {
    @statusCode _: 500;
    @body body: ApiError;
  };

  @post
  @route("/bookings")
  @tag("Bookings")
  @operationId("createBooking")
  @doc("Записать гостя на слот. Конец встречи считает сервер.")
  op createBooking(@body body: CreateBookingRequest):
    | {
        @statusCode _: 201;
        @body body: Booking;
      }
    | {
        @statusCode _: 400;
        @body body: ApiError;
      }
    | {
        @statusCode _: 404;
        @body body: ApiError;
      }
    | {
        @statusCode _: 409;
        @body body: ApiError;
      }
    | {
        @statusCode _: 500;
        @body body: ApiError;
      };
}
