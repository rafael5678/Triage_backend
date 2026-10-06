# Integración de Base de Datos y Pooler Supabase

Para entornos como Render donde el soporte IPv6 directo puede ser restringido, el sistema implementa en `DatasourceConfig.java` la resolución automática del pooler IPv4 de Supabase (puerto 6543 / 5432) garantizando conectividad ininterrumpida.
