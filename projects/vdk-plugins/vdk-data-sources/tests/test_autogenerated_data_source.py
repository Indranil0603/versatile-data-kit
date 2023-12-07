# Copyright 2021-2023 VMware, Inc.
# SPDX-License-Identifier: Apache-2.0
from datetime import datetime
from typing import List

from vdk.plugin.data_sources.auto_generated import (
    AutoGeneratedDataSource,
)
from vdk.plugin.data_sources.auto_generated import (
    AutoGeneratedDataSourceConfiguration,
)
from vdk.plugin.data_sources.auto_generated import (
    AutoGeneratedDataSourceStream,
)
from vdk.plugin.data_sources.data_source import DataSourcePayload
from vdk.plugin.data_sources.state import DataSourceStateFactory
from vdk.plugin.data_sources.state import InMemoryDataSourceStateStorage


def test_in_memory_data_source_stream():
    config = AutoGeneratedDataSourceConfiguration(
        num_records=3, include_metadata=True, num_streams=1
    )
    stream = AutoGeneratedDataSourceStream(config, 1)
    records: List[DataSourcePayload] = list(stream.read())
    assert len(records) == 3
    for record in records:
        assert "id" in record.data
        assert "name" in record.data
        assert "stream" in record.data
        assert "timestamp" in record.metadata
        assert isinstance(record.metadata["timestamp"], datetime)


def test_data_source_plugin():
    config = AutoGeneratedDataSourceConfiguration(
        num_records=3, include_metadata=True, num_streams=2
    )
    data_source = AutoGeneratedDataSource()

    data_source.configure(config)
    data_source.connect(
        DataSourceStateFactory(InMemoryDataSourceStateStorage()).get_data_source_state(
            "foo"
        )
    )
    streams = data_source.streams()
    assert len(streams) == 2

    for i, stream in enumerate(streams):
        records = list(stream.read())
        assert len(records) == 3
        for record in records:
            assert "id" in record.data
            assert "name" in record.data
            assert record.data["stream"] == i
            assert "timestamp" in record.metadata

    data_source.disconnect()
    assert len(data_source.streams()) == 0