import { Component, Input, OnInit, AfterViewInit, OnDestroy } from '@angular/core';
import * as c3 from 'c3';
import { PrimitiveArray } from 'c3';
import { ChartType, WordCloudTuple } from '../../types/visualization.interface';
import * as d3 from 'd3';
import * as cloud from 'd3-cloud';
import { WorkflowStatusService } from '../../service/workflow-status/workflow-status.service';
import { Subscription } from 'rxjs';
import { ResultObject } from '../../types/execute-workflow.interface';


/**
 * VisualizationPanelContentComponent displays the chart based on the chart type and data in table.
 *
 * It will convert the table into data format required by c3.js.
 * Then it passes the data and figure type to c3.js for rendering the figure.
 * @author Mingji Han, Xiaozhen Liu
 */
@Component({
  selector: 'texera-visualization-panel-content',
  templateUrl: './visualization-panel-content.component.html',
  styleUrls: ['./visualization-panel-content.component.scss']
})
export class VisualizationPanelContentComponent implements OnInit, AfterViewInit, OnDestroy {
  // this readonly variable must be the same as HTML element ID for visualization
  public static readonly CHART_ID = '#texera-result-chart-content';
  public static readonly WORD_CLOUD_ID = 'texera-word-cloud';
  public static readonly WIDTH = 1000;
  public static readonly HEIGHT = 700;

  @Input()
  public operatorID: string | undefined;

  private chartType: string | undefined;
  private columns: string[] = [];
  private data: object[] = [];

  private subscription: Subscription | undefined;

  private wordCloudElement: d3.Selection<SVGGElement, unknown, HTMLElement, any> | undefined;

  constructor(
    private workflowStatusService: WorkflowStatusService
  ) {
  }

  ngOnInit() {
  }

  ngAfterViewInit() {
    if (!this.operatorID) {
      return;
    }

    const currentResult: ResultObject | undefined = this.workflowStatusService.getCurrentResult()[this.operatorID];
    if (!currentResult || !currentResult.chartType) {
      return;
    }
    this.chartType = currentResult.chartType;
    this.data = currentResult.table as object[];
    this.columns = Object.keys(currentResult.table).filter(x => x !== '_id');

    if (this.chartType === ChartType.WORD_CLOUD) {
      this.wordCloudElement =
        d3.select(`#${VisualizationPanelContentComponent.WORD_CLOUD_ID}`)
              .append('svg')
      .attr('width', VisualizationPanelContentComponent.WIDTH)
      .attr('height', VisualizationPanelContentComponent.HEIGHT)
      .append('g')
      .attr('transform',
        'translate(' + VisualizationPanelContentComponent.WIDTH / 2 + ',' + VisualizationPanelContentComponent.HEIGHT / 2 + ')')
        ;

    }

    this.drawChart();

    this.subscription = this.workflowStatusService.getResultUpdateStream().auditTime(2000).subscribe(update => {
      if (!this.operatorID) {
        return;
      }
      const resultUpdate: ResultObject | undefined = this.workflowStatusService.getCurrentResult()[this.operatorID];
      if (!resultUpdate || !resultUpdate.chartType) {
        return;
      }
      this.chartType = resultUpdate.chartType;
      this.data = resultUpdate.table as object[];
      this.columns = Object.keys(resultUpdate.table).filter(x => x !== '_id');

      console.log('word cloud: drawing');
      this.drawChart();
    });

  }

  ngOnDestroy() {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
  }

  drawChart() {
    if (!this.chartType) {
      return;
    }
    switch (this.chartType) {
      // correspond to WordCloudSink.java
      case ChartType.WORD_CLOUD: this.onClickGenerateWordCloud(); break;
      // correspond to TexeraBarChart.java
      case ChartType.BAR:
      case ChartType.STACKED_BAR:
      // correspond to PieChartSink.java
      case ChartType.PIE:
      case ChartType.DONUT:
      // correspond to TexeraLineChart.java
      case ChartType.LINE:
      case ChartType.SPLINE: this.onClickGenerateChart(); break;
    }
  }

  onClickGenerateWordCloud() {
    if (!this.data) {
      return;
    }

    const wordCloudTuples = this.data as ReadonlyArray<WordCloudTuple>;

    const drawWordCloud = (words: cloud.Word[]) => {
      if (!this.wordCloudElement) {
        return;
      }

      const d3Fill = d3.scaleOrdinal(d3.schemeCategory10);

      const wordCloudData = this.wordCloudElement.selectAll<d3.BaseType, cloud.Word>('g text').data(words, d => d.text ?? '');

      wordCloudData.enter()
        .append('text')
        .style('font-size', (d) => d.size ?? 0 + 'px')
        .style('fill', d => d3Fill(d.text ?? ''))
        .attr('font-family', 'Impact')
        .attr('text-anchor', 'middle')
        .attr('transform', (d) => 'translate(' + [d.x, d.y] + ')rotate(' + d.rotate + ')')
        // this text() call must be at the end or it won't work
        .text(d => d.text ?? '')
        ;

      // Entering and existing words
      wordCloudData.transition()
        .duration(600)
        .attr('font-family', 'Impact')
        .style('font-size', d => d.size + 'px')
        .attr('transform', d => 'translate(' + [d.x, d.y] + ')rotate(' + d.rotate + ')')
        .style('fill-opacity', 1);

      // Exiting words
      wordCloudData.exit()
        .transition()
        .duration(200)
        .attr('font-family', 'Impact')
        .style('fill-opacity', 1e-6)
        .attr('font-size', 1)
        .remove();
    };

    const minCount = Math.min(...wordCloudTuples.map(t => t.count));
    const maxCount = Math.max(...wordCloudTuples.map(t => t.count));

    const minFontSize = 50;
    const maxFontSize = 150;

    const d3Scale = d3.scaleLinear();
    // const d3Scale = d3.scaleSqrt();
    // const d3Scale = d3.scaleLog();

    d3Scale.domain([minCount, maxCount]).range([minFontSize, maxFontSize]);

    const layout = cloud()
      .size([VisualizationPanelContentComponent.WIDTH, VisualizationPanelContentComponent.HEIGHT])
      .words(wordCloudTuples.map(t => ({ text: t.word, size: d3Scale(t.count) })))
      .text(d => d.text ?? '')
      .padding(5)
      .rotate(() => 0)
      .font('Impact')
      .fontSize(d => d.size ?? 0)
      .random(() => 1)
      .on('end', drawWordCloud);

    layout.start();

  }

  onClickGenerateChart() {
    if (!this.data) {
      return;
    }

    const dataToDisplay: Array<[string, ...PrimitiveArray]> = [];
    const category: string[] = [];
    for (let i = 1; i < this.columns?.length; i++) {
      category.push(this.columns[i]);
    }

    const columnCount = this.columns.length;

    for (const row of this.data) {
      const items: [string, ...PrimitiveArray] = [Object.values(row)[0]];
      for (let i = 1; i < columnCount; i++) {
        items.push(Number((Object.values(row)[i])));
      }
      dataToDisplay.push(items);
    }

    c3.generate({
      size: {
        height: VisualizationPanelContentComponent.HEIGHT,
        width: VisualizationPanelContentComponent.WIDTH
      },
      data: {
        columns: dataToDisplay,
        type: this.chartType as c3.ChartType
      },
      axis: {
        x: {
          type: 'category',
          categories: category
        }
      },
      bindto: VisualizationPanelContentComponent.CHART_ID
    });
  }

}
