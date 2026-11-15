"""Wyoming TTS proxy: spells out Polish numbers/dates (pl_normalize), then forwards to Piper.

HA talks to this server instead of Piper. It advertises no streaming synthesis, so HA always sends one
complete `synthesize` event and numbers are never split across text chunks. Audio is relayed unchanged.
"""
import argparse
import asyncio
import dataclasses
import logging
from functools import partial

from wyoming.audio import AudioStop
from wyoming.client import AsyncTcpClient
from wyoming.error import Error
from wyoming.event import Event
from wyoming.info import Describe, Info
from wyoming.server import AsyncEventHandler, AsyncServer
from wyoming.tts import Synthesize

from pl_normalize import normalize

_LOG = logging.getLogger("piper-pl")


class ProxyHandler(AsyncEventHandler):
    def __init__(self, upstream: tuple[str, int], name: str, *args, **kwargs) -> None:
        super().__init__(*args, **kwargs)
        self._upstream = upstream
        self._name = name

    async def handle_event(self, event: Event) -> bool:
        try:
            if Describe.is_type(event.type):
                await self.write_event((await self._describe()).event())
            elif Synthesize.is_type(event.type):
                await self._synthesize(Synthesize.from_event(event))
            # Streaming events (synthesize-start/chunk/stop) are not advertised, so ignore them.
        except Exception as err:  # noqa: BLE001 – report to HA instead of dropping the connection
            _LOG.exception("request failed")
            await self.write_event(Error(text=f"piper-pl: {err}", code=type(err).__name__).event())
        return True

    async def _describe(self) -> Info:
        async with AsyncTcpClient(*self._upstream) as client:
            await client.write_event(Describe().event())
            while (event := await client.read_event()) is not None:
                if Info.is_type(event.type):
                    info = Info.from_event(event)
                    tts = [dataclasses.replace(p, name=self._name, supports_synthesize_streaming=False)
                           for p in info.tts]
                    return dataclasses.replace(info, tts=tts)
        raise ConnectionError("Piper closed the connection without info")

    async def _synthesize(self, request: Synthesize) -> None:
        spoken = normalize(request.text)
        if spoken != request.text:
            _LOG.info("%r -> %r", request.text, spoken)
        async with AsyncTcpClient(*self._upstream) as client:
            await client.write_event(Synthesize(text=spoken, voice=request.voice).event())
            while (event := await client.read_event()) is not None:
                await self.write_event(event)
                if AudioStop.is_type(event.type) or Error.is_type(event.type):
                    return
        raise ConnectionError("Piper closed the connection before audio-stop")


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--uri", default="tcp://0.0.0.0:10201")
    parser.add_argument("--piper-host", default="127.0.0.1")
    parser.add_argument("--piper-port", type=int, default=10200)
    parser.add_argument("--name", default="piper-pl")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    server = AsyncServer.from_uri(args.uri)
    _LOG.info("listening on %s, forwarding to %s:%s", args.uri, args.piper_host, args.piper_port)
    await server.run(partial(ProxyHandler, (args.piper_host, args.piper_port), args.name))


if __name__ == "__main__":
    asyncio.run(main())
