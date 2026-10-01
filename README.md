# TriageIA — Backend (Spring Boot 3)

API REST para ingreso, scoring de criticidad (NEWS2 + matriz de lesión/enfermedad) y cola dinámica.

El esquema SQL vive en: https://github.com/rafael5678/Triage_base-de-datos  
Frontend: https://github.com/rafael5678/Triage_frotend-

JPA está anotado, pero `ddl-auto` es `none`: las tablas las crea el script SQL, no Hibernate.

```bash
mvn spring-boot:run
```

Base: `com.hospital.triage`  
API: `/api/v1/triage`
