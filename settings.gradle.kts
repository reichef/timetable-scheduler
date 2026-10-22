rootProject.name = "timetable-scheduler"

include(
    "domain",
    "application",
    "adapters:persistence",
    "adapters:web",
    "adapters:solver",
    "bootstrap",
)
