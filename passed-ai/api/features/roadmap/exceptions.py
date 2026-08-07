class RoadmapGenerationError(RuntimeError):
    """The model completed a request but did not produce an acceptable roadmap."""


class RoadmapConfigurationError(RuntimeError):
    """The LLM generator is enabled without the required runtime configuration."""
