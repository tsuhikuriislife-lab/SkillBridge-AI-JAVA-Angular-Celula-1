
from jobs.text_chunker import process_catalog


# Simulación de registros provenientes de HU-PS02
catalog_clean = [
    {
        "service_id": "SRV-001",
        "service_name": "Java Backend",
        "short_description": "Curso introductorio de desarrollo backend con Java.",
        "detail": (
            "En este servicio aprenderás programación orientada "
            "a objetos, estructuras de datos, manejo de excepciones, "
            "desarrollo de APIs REST y persistencia con Spring Boot. "
        ) * 40,
        "learning_objectives": (
            "Comprender Java, implementar clases, interfaces, "
            "inyección de dependencias y desarrollar APIs REST."
        ),
        "prerequisites": "Conocimientos básicos de programación."
    },
    {
        "service_id": "SRV-002",
        "service_name": "Angular Frontend",
        "short_description": "Desarrollo de interfaces con Angular.",
        "detail": (
            "Aprenderás componentes, servicios, routing, "
            "inyección de dependencias y consumo de APIs HTTP."
        ),
        "learning_objectives": (
            "Construir aplicaciones frontend utilizando Angular."
        ),
        "prerequisites": None
    }
]


# Ejecutar procesamiento
chunks = process_catalog(
    catalog=catalog_clean,
    chunk_size=512,
    overlap=0.10
)


# Mostrar resultados
print("TOTAL DE CHUNKS:", len(chunks))

for chunk in chunks:

    print("----------------------------------")
    print("Servicio:", chunk["service_id"])
    print("Campo:", chunk["source_field"])
    print("Índice:", chunk["chunk_index"])
    print("Tokens:", chunk["token_count"])
    print("Texto:", chunk["chunk_text"][:100])
