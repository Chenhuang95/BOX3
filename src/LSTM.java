import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import tools.FileUtil;

public class LSTM {
    public static void main(String[] args) {
        String ecsDataPath = "D:\\testdata\\TrainData_2015.1.1_2015.2.19.txt";
        // String[] ecsContent = FileUtil.read(ecsDataPath, null);
        String inputFilePath = "D:\\testdata\\input_5flavors_cpu_7days.txt";
        // String[] inputContent = FileUtil.read(inputFilePath, null);
        String resultFilePath = "D:\\testdata\\output_5flavors_cpu_7days.txt";

        String[] ecsContent = FileUtil.read(ecsDataPath, null);
        String[] inputContent = FileUtil.read(inputFilePath, null);

        String[] resultContents = predictVm(ecsContent, inputContent);

        FileUtil.write(resultFilePath, resultContents, false);

    }

    // 预测+装箱 一共多少天 days_of_ecsdata ，需要预测多少天day_predict_interval，2维矩阵15*总天数ces_data。。

    // 箱子数量，每个箱子装的个数 （String）

    public static String[] predictVm(String[] ecsContent, String[] inputContent){

        /*
         * 以下代码通过ecsdata.TXT导出所需信息 类型为 {56498c50-84e4 flavor15 2015-01-01 19:03:32}
         * days_of_ecsdata = 训练数据的总天数 ces_data = 2维矩阵15*总天数,即神经网络的输入数据；
         */
        String[][] ces_data_str = new String[ecsContent.length][4];
        for (int i = 0; i < ecsContent.length; i++) {
            ces_data_str[i] = ecsContent[i].split("\\s");
        }
        String ecs_start_time = ces_data_str[0][2] + " 00:00:00";
        String ecs_end_time = ces_data_str[ces_data_str.length - 1][2] + " 00:00:00";
        int days_of_ecsdata = calculate_days(ecs_start_time, ecs_end_time) + 1;
        int[][] ces_data = classifyData(ces_data_str, ces_data_str[0][2], ces_data_str[ces_data_str.length - 1][2],
                days_of_ecsdata);



        /* 以下代码通过intput信息TXT导出所需信息 String []servers = 物理服务器规格{cpu数量 内存大小 硬盘大小} CPU_num =
         * 一个服务器的cpu数量； MEM_num = 一个物理服务器的内存大小； kinds_of_ecs = 需要预测的虚拟服务器种类数量；
         * String[] ecs_for_predict = 需要预测的虚拟服务器种类和占用资源{flavor1 1 1024};
         * kinds_of_ecs =需要预测的虚拟服务器种类数量;
         */

        String[] servers = inputContent[0].split("\\s");// 物理服务器规格
        int CPU_num = Integer.parseInt(servers[0]);// 一个物理服务器的cpu数量
        int MEM_num = Integer.parseInt(servers[1]);// 一个物理服务器的内存大小
        //System.out.println("物理服务器cpu大小"+CPU_num);
        // System.out.println("物理服务器内存大小"+MEM_num);


        String threshold_choose = inputContent[inputContent.length - 4];// 根据输入文件选择装箱算法的决定量
        String predict_start_time = inputContent[inputContent.length - 2];// 预测开始时间
        String predict_end_time = inputContent[inputContent.length - 1];// 预测结束时间
        int day_predict_interval = calculate_days(predict_start_time, predict_end_time);
        int kinds_of_ecs = Integer.parseInt(inputContent[2]);// kinds_of_ecs为需要预测的虚拟服务器种类数量

        String[] ecs_for_predict = new String[kinds_of_ecs];
        System.arraycopy(inputContent, 3, ecs_for_predict, 0, kinds_of_ecs);
        String[][] ecs_for_predict_array = new String[kinds_of_ecs][3];// 输入虚拟器规格二维数组【flavor1】【1】【1024】
        for (int i = 0; i < kinds_of_ecs; i++) {
            String[] temp_ecs_for_predict = ecs_for_predict[i].split("\\s");
            ecs_for_predict_array[i] = temp_ecs_for_predict;
        }


        // predict_sult为预测的虚拟机的数量 1*15维矩阵
        //int[] predict_sult = predict(ces_data, day_predict_interval);
        int [] predict_sult = {20,20,20,20,20,50,20,20,20,20,20,20,20,20,20};

        int [][]box_out=PBB2(predict_sult,CPU_num,MEM_num,threshold_choose,ecs_for_predict);





        /*
         * 以下程序为输出的result 包括： ecs_num_sum =预测结果虚拟机的总量
         * String [][] ecs_for_predict_array = 虚拟机类型 种类{flavor1 2}
         * int servers_num = 需要物理服务器数量，即箱子数量
         * results[i+kinds_of_ecs+3] (i=0~servers_num-1) 为 {物理服务器标号 虚拟服务器种类 虚拟服务器数量}{1
         * flavor1 2}
         */
        int ecs_num_sum = 0;   //计算虚拟机总数
        int []ecs_num =new int[kinds_of_ecs];
        for(int i=0; i<kinds_of_ecs;i++)
        {
            ecs_num[i] = predict_sult[Integer.parseInt(ecs_for_predict_array[i][0].substring(6))-1];
            ecs_num_sum+=ecs_num[i];
        }

        int servers_num = box_out.length;
        String[] results = new String[kinds_of_ecs + servers_num + 3];// 储存输出结果
        results[0] = String.valueOf(ecs_num_sum);// 第一行 为预测结果虚拟机的总量
        results[kinds_of_ecs + 1] = "";// 输出文件中的空行
        results[kinds_of_ecs + 2] = String.valueOf(servers_num);// 物理服务器总数


        for (int i = 0; i < kinds_of_ecs; i++)// 预测结果，每种虚拟机的数量
        {
            results[i+1] = ecs_for_predict_array[i][0] + " " + ecs_num[i];
        }

        String []servers_write_data = new String[servers_num];//变成需要的输出格式 fl1 4 fl2 6.。。。
        for(int i =0;i<servers_num;i++)
        {
            servers_write_data[i] = String.valueOf(i + 1);
            for (int j=0;j<box_out[0].length;j++)
            {
                if(box_out[i][j] != 0)
                {
                    servers_write_data[i] = servers_write_data[i]+" "+ecs_for_predict_array[j][0]+" "+box_out[i][j];
                }
            }
        }

        for (int i = 0; i < servers_num; i++)// 输出每个物理服务器中的ecs预测结果和数量
        {
            results[i + kinds_of_ecs + 3] = servers_write_data[i];
        }
        return results;
    }





    //上方为主要函数，下方为功能函数



//PPB2为装箱方法，int [][]box_out =PPB2();
    //第一行为所有flaover的数量排列          第一行{f1_num f2_num f3_num....}

    private static int[][] PBB2(int[] predict_sult, int cpu_num,int mem_num, String threshold_choose,
                                String[] ecs_for_predict) {
        String[][] Str_server = new String[ecs_for_predict.length][3];
        int[][] VirtualServerParamTable = new int[ecs_for_predict.length][3];// 服务器的参数表
        List<flavor> flavor_list = new ArrayList<>();//创建flavor列表
        // List<Box> Box_list=new ArrayList<>();//创建Box链表
        // Box_list.add(new Box(cpu_num,mem_num,ecs_for_predict.length));//创建第一个盒子
        int ecs_num_sum =0;

        int all_cpu_use=0;//计算虚拟机需要的cpu资源总和
        int all_mem_use=0;//计算虚拟机需要的mem资源总和

        //flavor列表填入数据
        for (int i = 0; i < ecs_for_predict.length; i++) {
            Str_server[i] = ecs_for_predict[i].split("\\s");
            VirtualServerParamTable[i][0] = Integer.parseInt(Str_server[i][0].substring(6));//标号
            VirtualServerParamTable[i][1] =Integer.parseInt(Str_server[i][1]);//cpu
            VirtualServerParamTable[i][2]=Integer.parseInt(Str_server[i][2]);//mem
            int temp_ecs_num = predict_sult[VirtualServerParamTable[i][0]-1];//数量
            for(int j=0;j<temp_ecs_num;j++)//每一个虚拟服务器添加进列表
            {
                flavor_list.add(new flavor(VirtualServerParamTable[i][0],VirtualServerParamTable[i][1],VirtualServerParamTable[i][2],i));
            }
            all_cpu_use+=VirtualServerParamTable[i][1]*temp_ecs_num;
            all_mem_use+=VirtualServerParamTable[i][2]*temp_ecs_num;

            ecs_num_sum =temp_ecs_num+ecs_num_sum;//计算需要放置的服务器总数，flavor_list的总数
        }
        System.out.println("all_cpu_use  "+all_cpu_use);
        System.out.println("all_mem_use  "+all_mem_use);
        System.out.println("flavor_list.size"+flavor_list.size());

        int T = 100;// 初始化温度
        double Tmin = 1e-3;// 温度的下界
        int count = 500;// 迭代的次数
        double delta = 0.98;// 温度的下降率
        //如果判定标准为MEM
        List<Box> Box_out = new ArrayList<>();
        if(threshold_choose.equals("MEM")) {
            //虚拟服务器按mem大小排序
            Collections.sort(flavor_list, new Comparator<flavor>() {

                @Override
                public int compare(flavor o1, flavor o2) {
                    int i = o2.getMem() - o1.getMem();//首先按mem排序，从大到小
                    if (i == 0) {
                        return o2.getCpu() - o1.getCpu();
                    }
                    return i;
                }
            });

            List<Box> B = flavor2box(flavor_list, cpu_num, mem_num, ecs_for_predict.length);//首次装箱
            double t = T;

            List<flavor> F_temp = new ArrayList<>(flavor_list);
            List<Box> Box_temp = flavor2box(F_temp, cpu_num, mem_num, ecs_for_predict.length);
            while (t > Tmin) {
                for (int i = 0; i < count; i++) {
                    // 计算此时的函数结果
                    List<flavor> F_temp_new = new ArrayList<>(F_temp);
                    double mem_score = (0.0 + all_mem_use) / (0.0 + mem_num * Box_temp.size() * 1024);
                    // 在邻域内产生新的解
                    int[] changenum = randomCommon(0, flavor_list.size(), 2);
                    Collections.swap(F_temp_new, changenum[0], changenum[1]);//虚拟机列表交换位置
                    List<Box> Box_temp_new = flavor2box(F_temp_new, cpu_num, mem_num, ecs_for_predict.length);//首次装箱
                    // 计算新的函数结果并对比
                    double mem_score_new = (0.0 + all_mem_use) / (0.0 + mem_num * Box_temp_new.size() * 1024);
                    if (mem_score_new - mem_score > 0) {
                        // 替换
                        Box_temp = new ArrayList<>(Box_temp_new);
                        F_temp = new ArrayList(F_temp_new);
                    } else {
                        // 以概率替换
                        double p = 1 / (1 + Math
                                .exp(-(mem_score_new - mem_score) / T));
                        if (Math.random() < p) {
                            F_temp = new ArrayList(F_temp_new);
                            Box_temp = new ArrayList<>(Box_temp_new);
                        }
                    }
                }
                t = t * delta;
            }

            Box_out = flavor2box(F_temp, cpu_num, mem_num, ecs_for_predict.length);

        }




//以下为cpu作为衡量标准
    else if(threshold_choose.equals("CPU"))
    {
        Collections.sort(flavor_list, new Comparator<flavor>()
        {

            @Override
            public int compare(flavor o1, flavor o2) {
                int i = o2.getCpu() - o1.getCpu();//首先按mem排序，从大到小
                if (i == 0) {
                    return o2.getMem() - o1.getMem();
                }
                return i;
            }
        });

        List<Box> B = flavor2box(flavor_list, cpu_num, mem_num, ecs_for_predict.length);//首次装箱
        double t = T;

        List<flavor> F_temp = new ArrayList<>(flavor_list);
        List<Box> Box_temp = flavor2box(F_temp, cpu_num, mem_num, ecs_for_predict.length);
        while (t > Tmin) {
            for (int i = 0; i < count; i++) {
                // 计算此时的函数结果
                List<flavor> F_temp_new = new ArrayList<>(F_temp);
                double mem_score = (0.0 + all_cpu_use) / (0.0 + cpu_num * Box_temp.size());
                // 在邻域内产生新的解
                int[] changenum = randomCommon(0, flavor_list.size(), 2);
                Collections.swap(F_temp_new, changenum[0], changenum[1]);//虚拟机列表交换位置
                List<Box> Box_temp_new = flavor2box(F_temp_new, cpu_num, mem_num, ecs_for_predict.length);//首次装箱
                // 计算新的函数结果并对比
                double mem_score_new = (0.0 + all_cpu_use) / (0.0 + cpu_num * Box_temp_new.size());
                if (mem_score_new - mem_score > 0) {
                    // 替换
                    Box_temp = new ArrayList<>(Box_temp_new);
                    F_temp = new ArrayList(F_temp_new);
                } else {
                    // 以概率替换
                    double p = 1 / (1 + Math
                            .exp(-(mem_score_new - mem_score) / T));
                    if (Math.random() < p) {
                        F_temp = new ArrayList(F_temp_new);
                        Box_temp = new ArrayList<>(Box_temp_new);
                    }
                }
            }
            t = t * delta;
        }

        Box_out = flavor2box(F_temp, cpu_num, mem_num, ecs_for_predict.length);

    }


    int [][]PhysicalServerPlace =new int[Box_out.size()][ecs_for_predict.length];
        for(int i =0;i<Box_out.size();i++)
    {
        System.arraycopy(Box_out.get(i).ecs_buffer,0,PhysicalServerPlace[i],0,ecs_for_predict.length);

    }


    double mem_rate = (0.0+all_mem_use)/(0.0+mem_num*Box_out.size()*1024);
        System.out.println("mem_rate: "+mem_rate);
        double cpu_rate = (0.0+all_cpu_use)/(0.0+cpu_num*Box_out.size());
        System.out.println("cpu_rate : "+cpu_rate);

    int kkkkkkkk=0;
        for(int i =0;i<PhysicalServerPlace.length;i++)
    {
        for(int j =0;j<PhysicalServerPlace[0].length;j++)
        {
            kkkkkkkk+=PhysicalServerPlace[i][j];
        }
    }
        System.out.println("box中ecs数量 : " +kkkkkkkk);

        return PhysicalServerPlace;

}

    //预测函数过程 lstm算法：
    static int[] predict(int[][] ces_data, int day_predict_interval) {
        final Self self = new Self();

        int cur_n = 1000; // 迭代次数
        ////////////////////////////////////////////////////////////////////////////////////////// 平滑
        double[][] Input = new double[ces_data.length][ces_data[0].length];
        // Input = (float[][]) DataResult;
        for (int i = 0; i < ces_data.length; i++) {
            for (int j = 0; j < ces_data[0].length; j++) {

                Input[i][j] = ces_data[i][j];

            }

        }

        double[][] smooth_result = Smooth_fuction(Input);//进行平滑

        // 取七天求和进行预测：
        double[][] sum_result = new double[smooth_result.length][smooth_result[0].length - day_predict_interval];
        for (int i = 0; i < smooth_result.length; i++) {
            for (int j = 0; j < smooth_result[0].length - day_predict_interval; j++) {
                double sum = 0;
                for (int k = j; k < j + day_predict_interval; k++) {
                    sum = sum + smooth_result[i][k];
                }
                sum_result[i][j] = sum;
            }
        }
//以下为归一化过程
        //double[] max = new double[sum_result.length];
        double[] data_mean = new double[sum_result.length];//计算方差
        double[] data_std_var = new double[sum_result.length];//计算标准差
        double[] data_var = new double[sum_result.length];//方差
        for (int i = 0; i < sum_result.length; i++) {
            data_mean[i] = getAverage(sum_result[i]);
            data_std_var[i] = StandardDiviation(sum_result[i]);
            data_var[i] =  data_std_var[i]*data_std_var[i];
        }

        double[][] guiyi_result = new double[sum_result.length][sum_result[0].length];//归一化

        for (int i = 0; i < sum_result.length; i++) {
            for (int j = 0; j < sum_result[0].length; j++) {
                guiyi_result[i][j] = (sum_result[i][j]-data_mean[i]) / data_var[i];
            }
        }

        for (int i = 0; i < guiyi_result.length; i++) {
            for (int j = 0; j < guiyi_result[0].length; j++) {
                System.out.print(guiyi_result[i][j] + " ");
            }
            System.out.println();
        }
        System.out.println("----------" + guiyi_result[0].length);

        /////////////////////////////////////////////////////////////////////////////////////////// 预测

        int mem_cell_ct = 60;// 隐含层的个数

        int Len_data = guiyi_result[0].length;
        int x_dim = 30; // 训练时间长度（天） 先用前20个数据去预测第27个数据得出模型
        LstmPara lstmPara = new LstmPara();
        lstmPara.initialize(self, mem_cell_ct, x_dim, 0.2);
        ListNetwork lstmNet = new ListNetwork();
        lstmNet.initialize(self);
        double[][] er = new double[36][cur_n];
        for (int cur_iter = 0; cur_iter < cur_n; cur_iter++) {
            int day_count = (int) (Math.random() * (Len_data - day_predict_interval - x_dim));

            double[] y_list = new double[guiyi_result.length]; // 需要第50天数据
            for (int i = 0; i < guiyi_result.length; i++) {
                y_list[i] = guiyi_result[i][day_count + x_dim + day_predict_interval - 1];
            }
            // 自己定义的输入前20天数据数据
            double[][] input_val_arr = new double[y_list.length][x_dim];
            for (int m = 0; m < y_list.length; m++) {
                for (int i = day_count; i < day_count + x_dim; i++) {
                    input_val_arr[m][i - day_count] = guiyi_result[m][i];
                }
            }
            for (int ind = 0; ind < y_list.length; ind++) {
                ListNetwork.x_list_add(self, input_val_arr[ind], ind);
                if (cur_iter == cur_n - 1) {
                    // System.out.println("训练" + self.h[ind][0] + "实际" + " " + y_list[ind]);
                }
            }
            ToylossLayer loss_layer = new ToylossLayer();
            double loss = ListNetwork.y_list_is(self, y_list, loss_layer, input_val_arr);

            //  System.out.println("误差值" + loss);
            lstmPara.apply_diff(self, 0.4);
            lstmNet.x_list_clear(self);

            er[day_count][cur_iter] = loss;

        }
        //System.out.println("Train Over");

        ListNetwork lstmNetPre = new ListNetwork();
        // double[] y_list2 = new double[guiyi_result.length]; // 需要预测第51天数据
        // for (int i = 0; i < 15; i++) {
        // y_list2[i] = guiyi_result[i][Len_data - 1];
        // } // 需要训练的一维数据
        // 自己定义的输入2~50天数据
        double[][] input_val_arr2 = new double[guiyi_result.length][x_dim];
        for (int m = 0; m < guiyi_result.length; m++) {
            for (int i = Len_data - x_dim; i <= Len_data - 1; i++) {
                input_val_arr2[m][i - (Len_data - x_dim)] = guiyi_result[m][i];
            }
        }

        int[] result = new int[guiyi_result.length];
        // for (int cur_iter = 0; cur_iter < 1; cur_iter++) {
        for (int ind = 0; ind < guiyi_result.length; ind++) {
            ListNetwork.x_list_add(self, input_val_arr2[ind], ind);

            // if (cur_iter == 99) {
            result[ind] = (int) Math.round((self.h[ind][0] * data_var[ind])+data_mean[ind]);//归一化结果，还原数据
            System.out.println("预测" + result[ind]);
        }
        // }

        // lstmNetPre.x_list_clear(self);

        // }
        // ToylossLayer loss_layer = new ToylossLayer();
        // double loss = ListNetwork.y_list_is(self, y_list2, loss_layer,
        // input_val_arr2);
        // System.out.println("\n" + loss);

        return result;

    }

    private static double find_max(double[] input) {
        // TODO Auto-generated method stub
        double temp = 0;
        for (int i = 0; i < input.length; i++) {
            if (temp < input[i]) {
                temp = input[i];
            }
        }
        return temp;
    }

    //装箱算法
    static List<Box> flavor2box(List<flavor> flavor_list,int cpu_num,int mem_num,int length)
    {
        List <flavor>F = new ArrayList<> (flavor_list);
        List <Box>Box_list = new ArrayList<> ();
        Box_list.add(new Box(cpu_num,mem_num,length));//创建第一个盒子


        //首次装箱
        for(int i =0;i<F.size();i++) {
            boolean r = false;
            for (int j = 0; j < Box_list.size(); j++) {

                if ((flavor_list.get(i).getMem() <= Box_list.get(j).MEM_left) && (flavor_list.get(i).getCpu() <= Box_list.get(j).CPU_left)) {
                    Box_list.get(j).MEM_left -= flavor_list.get(i).getMem();
                    Box_list.get(j).CPU_left -= flavor_list.get(i).getCpu();
                    Box_list.get(j).ecs_buffer[flavor_list.get(i).flavor_count]++;
                    r = true;
                    break;
                }

            }
            if (!r) {
                Box_list.add(new Box(cpu_num, mem_num, length));//如果盒子放不下则创建个盒子
                Box_list.get(Box_list.size() - 1).MEM_left -= flavor_list.get(i).getMem();
                Box_list.get(Box_list.size() - 1).CPU_left -= flavor_list.get(i).getCpu();
                Box_list.get(Box_list.size() - 1).ecs_buffer[flavor_list.get(i).flavor_count]++;
            }
        }

        return Box_list;
    }

    /**
     * 调换集合中两个指定位置的元素, 若两个元素位置中间还有其他元素，需要实现中间元素的前移或后移的操作。
     * @param list 集合
     * @param oldPosition 需要调换的元素
     * @param newPosition 被调换的元素
     * @param <flavor>
     */
    public static <flavor> void swap1(List<flavor> list, int oldPosition, int newPosition){
        if(null == list){
            throw new IllegalStateException("The list can not be empty...");
        }
        flavor tempElement = list.get(oldPosition);

        // 向前移动，前面的元素需要向后移动
        if(oldPosition < newPosition){
            for(int i = oldPosition; i < newPosition; i++){
                list.set(i, list.get(i + 1));
            }
            list.set(newPosition, tempElement);
        }
        // 向后移动，后面的元素需要向前移动
        if(oldPosition > newPosition){
            for(int i = oldPosition; i > newPosition; i--){
                list.set(i, list.get(i - 1));
            }
            list.set(newPosition, tempElement);
        }
    }
    //输入数据平滑函数  输入15*n维，输出15*n维
//所有数据，二维数组的平滑
    public static double[][] Smooth_fuction(double[][] Input)
    {
        double [][] Input_copy = new double[Input.length][Input[0].length];
        System.arraycopy(Input,0,Input_copy,0,Input.length);
        double [][] Data_for_predict = new double[Input.length][Input[0].length];
        for(int j = 0;j<Input.length;j++)
        {
            Data_for_predict[j] = Second_exponential_smoothing(Input_copy[j]);
        }

        return Data_for_predict;
    }


    //一行数据二次指数平滑
    public static double[] Second_exponential_smoothing(double[] Input)
    {
        double []X = Input;//输入数据
        double []S = new double[Input.length];//平滑后的数据
        double []t = new double[Input.length];//t为平滑后的趋势
        double []data_after_smooth = new double[Input.length];
        System.arraycopy(X, 0, data_after_smooth, 0, Input.length);

        S[0] = Input[0];
        t[0] = 0;
        double a = 0.1;
        double b= 0.5;
        double Input_StandardDiviation = StandardDiviation(Input);
        double threshold = Input_StandardDiviation*3;//判决门限为3倍的标准差
        System.out.println("输入数据的标准差为："+Input_StandardDiviation);
        for(int i=1 ; i<Input.length ; i++)
        {
            S[i] = a*X[i]+(1-a)*(S[i-1]+t[i-1]);
            t[i] = b*(S[i]-S[i-1])+(1-b)*t[i-1];
            if ((X[i]>threshold)&&(X[i]>10))//如果超过门限就做平滑处理
            {
                data_after_smooth[i] = Math.round(S[i]) ;
                System.out.println("第"+(i+1)+"号的值为："+X[i]);
            }
        }
        return data_after_smooth;
    }

    //标准差σ=sqrt(s^2)的计算
    public static double StandardDiviation(double[] x) {
        int m=x.length;
        double sum=0;
        for(int i=0;i<m;i++){//求和
            sum+=x[i];
        }
        double dAve=sum/m;//求平均值
        double dVar=0;
        for(int i=0;i<m;i++){//求方差
            dVar+=(x[i]-dAve)*(x[i]-dAve);
        }
        return Math.sqrt(dVar/m);
    }

    //一维数组均值的计算
    public static double getAverage(double[] In_array) {
        double sum_temp=0;
        for(int i=0;i<In_array.length;i++){//求和
            sum_temp+=In_array[i];
        }
        double Mean=sum_temp/In_array.length;//求平均值
        return Mean;
    }

    // calculate_days为计算时间间隔天数
    public static int calculate_days(String start_time,String end_time){
        int calculate_result = 0;
        //Calendar calendar = Calendar.getInstance();

        SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            Date start_day = ft.parse(start_time);
            Date end_day = ft.parse(end_time);
            long days_need_predict = (end_day.getTime()-start_day.getTime())/ 1000 / 60 / 60 / 24;
            calculate_result = new Long(days_need_predict).intValue();
        } catch (ParseException e) {
            e.printStackTrace();
        }


        return calculate_result;
    }

    public static int[][]classifyData(String [][]str,String start_day,String end_day,int day_interval) {
        Calendar calendar = Calendar.getInstance();
        int DataResult[][] = new int[15][day_interval];
        // 对 calendar 设置时间的方法
        // 设置传入的时间格式
        long quot = 0;
        SimpleDateFormat ftt = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        try {
            Date date1 = ftt.parse(start_day);
            Date date2 = ftt.parse(end_day);

            // 指定一个日期，这个日期是数据的日期
            Date date = dateFormat.parse(start_day);
            // 对 calendar 设置为 date 所定的日期
            calendar.setTime(date);
            // System.out.println(dateFormat.format(calendar.getTime()));

            int daycount = 0;
            for (int k = 0; k < str.length; k++) {
                if (str[k][2].equals(dateFormat.format(calendar.getTime()))) {
                    switch (str[k][1]) {
                        case "flavor1":
                            DataResult[0][daycount]++;
                            break;
                        case "flavor2":
                            DataResult[1][daycount]++;
                            break;
                        case "flavor3":
                            DataResult[2][daycount]++;
                            break;
                        case "flavor4":
                            DataResult[3][daycount]++;
                            break;
                        case "flavor5":
                            DataResult[4][daycount]++;
                            break;
                        case "flavor6":
                            DataResult[5][daycount]++;
                            break;
                        case "flavor7":
                            DataResult[6][daycount]++;
                            break;
                        case "flavor8":
                            DataResult[7][daycount]++;
                            break;
                        case "flavor9":
                            DataResult[8][daycount]++;
                            break;
                        case "flavor10":
                            DataResult[9][daycount]++;
                            break;
                        case "flavor11":
                            DataResult[10][daycount]++;
                            break;
                        case "flavor12":
                            DataResult[11][daycount]++;
                            break;
                        case "flavor13":
                            DataResult[12][daycount]++;
                            break;
                        case "flavor14":
                            DataResult[13][daycount]++;
                            break;
                        case "flavor15":
                            DataResult[14][daycount]++;
                            break;
                    }
                } else {
                    calendar.add(calendar.DATE, +1);//时间天数加一
                    daycount = daycount + 1;
                    k--;

                }
            }
        } catch (Exception e) {
            e.printStackTrace();

        }
        return DataResult;
    }

    /**
     * 随机指定范围内N个不重复的数
     * 最简单最基本的方法
     * @param min 指定范围最小值
     * @param max 指定范围最大值
     * @param n 随机数个数
     */
    public static int[] randomCommon(int min, int max, int n){
        if (n > (max - min + 1) || max < min) {
            return null;
        }
        int[] result = new int[n];
        int count = 0;
        while(count < n) {
            int num = (int) (Math.random() * (max - min)) + min;
            boolean flag = true;
            for (int j = 0; j < n; j++) {
                if(num == result[j]){
                    flag = false;
                    break;
                }
            }
            if(flag){
                result[count] = num;
                count++;
            }
        }
        return result;
    }

}


//以下为定义的一些类
class ListNetwork {

    public void initialize(Self self) {
        // TODO Auto-generated method stub
        self.lstm_node_list.clear();
        self.x_list.clear();

    }

    public static void x_list_add(Self self, double[] x, int ind) {
        // TODO Auto-generated method stub

        self.x_list.add(x);
        LstmNode lstm_node = new LstmNode();
        LstmState lstm_state = new LstmState();
        if (self.x_list.size() > self.lstm_node_list.size()) {
            lstm_state.initialize(self, ind);
            lstm_node.initialize(self, lstm_state);
            self.lstm_node_list.add(lstm_node);
        }
        int idx = self.x_list.size() - 1;
        if (idx == 0) {

            self.lstm_node_list.get(idx).bottom_data_is(self, x, idx);

        } else {
            double[] s_prev = self.s[ind - 1];
            double[] h_prev = self.h[ind - 1];
            self.lstm_node_list.get(idx).bottom_data_is(self, x, idx, s_prev, h_prev);
        }
    }

    public static double y_list_is(Self self, double[] y_list, ToylossLayer loss_layer, double[][] input_val_arr) {
        // TODO Auto-generated method stub
        int idx = y_list.length - 1;
        double loss = loss_layer.loss(self.h[idx], y_list[idx]);
        double[] diff_h = loss_layer.bottom_diff(self.h[idx], y_list[idx]);
        double[] diff_s = new double[self.men_cell_ct];
        self.lstm_node_list.get(idx).top_diff_is(self, diff_h, idx, diff_s, input_val_arr[idx]);
        idx--;
        while (idx >= 0) {
            loss = loss + loss_layer.loss(self.h[idx], y_list[idx]);
            diff_h = loss_layer.bottom_diff(self.h[idx], y_list[idx]);
            for (int i = 0; i < diff_h.length; i++) {
                diff_h[i] = diff_h[i] + self.bottom_diff_h[idx + 1][i];
            }
            diff_s = self.bottom_diff_s[idx + 1];
            self.lstm_node_list.get(idx).top_diff_is(self, diff_h, idx, diff_s, input_val_arr[idx]);
            idx--;

        }
        return loss;
    }

    public void x_list_clear(Self self) {
        // TODO Auto-generated method stub
        self.x_list.clear();
    }

}


class LstmNode {

    public void initialize(Self self, LstmState lstm_state) {
        // TODO Auto-generated method stub
        self.state = lstm_state;
        self.x = null;
        self.xc = null;
    }

    public void bottom_data_is(Self self, double[] x, int idx) {
        // TODO Auto-generated method stub
        self.s_prev[idx] = new double[self.s[0].length];
        self.h_prev[idx] = new double[self.h[0].length];
        self.xc = new double[self.x_dim + self.h[0].length];
        self.x = x;
        for (int i = 0; i < self.x_dim; i++) {
            self.xc[i] = x[i];
        }
        for (int j = self.x_dim; j < self.h[0].length + self.x_dim; j++) {
            self.xc[j] = self.h_prev[idx][j - self.x_dim];
        }
        double[] wgx = Self.Matrix_multiplication(self.wg, self.xc);
        double[] wix = Self.Matrix_multiplication(self.wi, self.xc);
        double[] wfx = Self.Matrix_multiplication(self.wf, self.xc);
        double[] wox = Self.Matrix_multiplication(self.wo, self.xc);
        for (int k = 0; k < self.men_cell_ct; k++) {
            self.g[0][k] = Math.tanh(wgx[k] + self.bg[k]);
            self.i[0][k] = 1d / (1d + Math.exp(-wix[k] - self.bi[k]));
            self.f[0][k] = 1d / (1d + Math.exp(-wfx[k] - self.bf[k]));
            self.o[0][k] = 1d / (1d + Math.exp(-wox[k] - self.bo[k]));
        }
        for (int n = 0; n < self.s[0].length; n++) {
            self.s[0][n] = self.g[0][n] * self.i[0][n] + self.f[0][n] * self.s_prev[idx][n];
            self.h[0][n] = self.s[0][n] * self.o[0][n];
        }

    }

    public void bottom_data_is(Self self, double[] x, int ind, double[] s_prev, double[] h_prev) {
        // TODO Auto-generated method stub
        self.s_prev[ind] = s_prev;
        self.h_prev[ind] = h_prev;
        self.xc = new double[self.x_dim + self.h[0].length];
        for (int i = 0; i < self.x_dim; i++) {
            self.xc[i] = x[i];
        }
        for (int j = self.x_dim; j < self.h[0].length + self.x_dim; j++) {
            self.xc[j] = self.h_prev[ind][j - self.x_dim];
        }
        double[] wgx = self.Matrix_multiplication(self.wg, self.xc);
        double[] wix = self.Matrix_multiplication(self.wi, self.xc);
        double[] wfx = self.Matrix_multiplication(self.wf, self.xc);
        double[] wox = self.Matrix_multiplication(self.wo, self.xc);
        for (int k = 0; k < self.men_cell_ct; k++) {

            self.g[ind][k] = Math.tanh(wgx[k] + self.bg[k]);
            self.i[ind][k] = 1d / (1d + Math.exp(-wix[k] - self.bi[k]));
            self.f[ind][k] = 1d / (1d + Math.exp(-wfx[k] - self.bf[k]));
            self.o[ind][k] = 1d / (1d + Math.exp(-wox[k] - self.bo[k]));
        }
        for (int n = 0; n < self.s[0].length; n++) {
            self.s[ind][n] = self.g[ind][n] * self.i[ind][n] + self.f[ind][n] * self.s_prev[ind][n];
            self.h[ind][n] = self.s[ind][n] * self.o[ind][n];
        }
    }

    public void top_diff_is(Self self, double[] top_diff_h, int idx, double[] top_diff_s, double[] x) {

        double[] xc = new double[self.x_dim + self.h[0].length];
        for (int i = 0; i < self.x_dim; i++) {
            xc[i] = x[i];
        }
        for (int j = self.x_dim; j < self.h[0].length + self.x_dim; j++) {
            xc[j] = self.h_prev[idx][j - self.x_dim];
        }

        // TODO Auto-generated method stub
        double[] ds = new double[top_diff_h.length];
        double[] doo = new double[top_diff_h.length];
        double[] di = new double[top_diff_h.length];
        double[] dg = new double[top_diff_h.length];
        double[] df = new double[top_diff_h.length];
        double[] di_input = new double[top_diff_h.length];
        double[] df_input = new double[top_diff_h.length];
        double[] do_input = new double[top_diff_h.length];
        double[] dg_input = new double[top_diff_h.length];

        for (int i = 0; i < top_diff_h.length; i++) {
            ds[i] = self.o[idx][i] * top_diff_h[i] + top_diff_s[i];
            doo[i] = self.s[idx][i] * top_diff_h[i];

            di[i] = self.g[idx][i] * ds[i];
            dg[i] = self.i[idx][i] * ds[i];
            df[i] = self.s_prev[idx][i] * ds[i];
            di_input[i] = (1 - self.i[idx][i]) * self.i[idx][i] * di[i];
            df_input[i] = (1 - self.f[idx][i]) * self.f[idx][i] * df[i];
            do_input[i] = (1 - self.o[idx][i]) * self.o[idx][i] * doo[i];
            dg_input[i] = (1 - self.g[idx][i] * self.g[idx][i]) * dg[i];
        }

        for (int i = 0; i < top_diff_h.length; i++) {
            for (int j = 0; j < self.xc.length; j++) {
                self.wi_diff[i][j] = self.wi_diff[i][j] + di_input[i] * xc[j];
                self.wf_diff[i][j] = self.wf_diff[i][j] + df_input[i] * xc[j];
                self.wo_diff[i][j] = self.wo_diff[i][j] + do_input[i] * xc[j];
                self.wg_diff[i][j] = self.wg_diff[i][j] + dg_input[i] * xc[j];
            }

            for (i = 0; i < top_diff_h.length; i++) {
                self.bi_diff[i] = self.bi_diff[i] + di_input[i];
                self.bf_diff[i] = self.bf_diff[i] + df_input[i];
                self.bo_diff[i] = self.bo_diff[i] + do_input[i];
                self.bg_diff[i] = self.bg_diff[i] + dg_input[i];
            }
            double[][] wiT = new double[self.wi[0].length][self.wi.length];
            double[][] wfT = new double[self.wi[0].length][self.wi.length];
            double[][] woT = new double[self.wi[0].length][self.wi.length];
            double[][] wgT = new double[self.wi[0].length][self.wi.length];
            for (i = 0; i < self.wi.length; i++) {
                for (int j = 0; j < self.wi[0].length; j++) {
                    wiT[j][i] = self.wi[i][j];
                    wfT[j][i] = self.wf[i][j];
                    woT[j][i] = self.wo[i][j];
                    wgT[j][i] = self.wg[i][j];
                }
            }
            double[] dxc_temp = new double[self.xc.length];
            dxc_temp = self.Matrix_multiplication(wiT, di_input);
            double[] dxc = new double[self.xc.length];
            for (i = 0; i < self.xc.length; i++) {
                dxc[i] = dxc_temp[i] + dxc[i];
            }

            dxc_temp = self.Matrix_multiplication(wfT, df_input);
            for (i = 0; i < self.xc.length; i++) {
                dxc[i] = dxc_temp[i] + dxc[i];
            }

            dxc_temp = self.Matrix_multiplication(woT, do_input);
            for (i = 0; i < self.xc.length; i++) {
                dxc[i] = dxc_temp[i] + dxc[i];
            }

            dxc_temp = self.Matrix_multiplication(wgT, dg_input);
            for (i = 0; i < self.xc.length; i++) {
                dxc[i] = dxc_temp[i] + dxc[i];
            }
            for (i = 0; i < ds.length; i++) {
                self.bottom_diff_s[idx][i] = ds[i] * self.f[idx][i];
            }
            for (i = 0; i < self.x_dim; i++) {
                self.bottom_diff_x[idx][i] = dxc[i];
            }
            for (i = self.x_dim; i < self.men_cell_ct + self.x_dim; i++) {
                self.bottom_diff_h[idx][i - self.x_dim] = dxc[i];
            }

        }

    }

}

class LstmPara {

    public void initialize(Self self, int mem_cell_ct, int x_dim, double a) {
        // TODO Auto-generated method stub
        // self.mem_cell_ct = mem_cell_ct
        self.men_cell_ct = mem_cell_ct;
        self.x_dim = x_dim;
        int concat_len = x_dim + mem_cell_ct;
        // self.x_dim = x_dim
        // concat_len = x_dim + mem_cell_ct
        // # weight matrices
        double[][] array_wg = new double[mem_cell_ct][concat_len];
        double[][] array_wi = new double[mem_cell_ct][concat_len];
        double[][] array_wf = new double[mem_cell_ct][concat_len];
        double[][] array_wo = new double[mem_cell_ct][concat_len];

        for (int i = 0; i < array_wg.length; i++) {
            for (int h = 0; h < array_wg[i].length; h++) {
                // array_wg[i][h] = 0.2 * (new Random().nextDouble()) - 0.1; // 赋值：1以内的随机数
                // array_wi[i][h] = 0.2 * (new Random().nextDouble()) - 0.1;
                // array_wf[i][h] = 0.2 * (new Random().nextDouble()) - 0.1;
                // array_wo[i][h] = 0.2 * (new Random().nextDouble()) - 0.1;
                array_wg[i][h] = a * (new Random().nextDouble()) - a / 2; // 赋值：1以内的随机数
                array_wi[i][h] = a * (new Random().nextDouble()) - a / 2;
                array_wf[i][h] = a * (new Random().nextDouble()) - a / 2;
                array_wo[i][h] = a * (new Random().nextDouble()) - a / 2;
            }
        }
        self.wg = array_wg;
        self.wi = array_wi;
        self.wf = array_wf;
        self.wo = array_wo;

        double[] array_bg = new double[mem_cell_ct];
        double[] array_bi = new double[mem_cell_ct];
        double[] array_bf = new double[mem_cell_ct];
        double[] array_bo = new double[mem_cell_ct];

        for (int i = 0; i < array_bg.length; i++) {
            // array_bg[i] = 0.2 * (new Random().nextDouble()) - 0.1;
            // array_bi[i] = 0.2 * (new Random().nextDouble()) - 0.1;
            // array_bf[i] = 0.2 * (new Random().nextDouble()) - 0.1;
            // array_bo[i] = 0.2 * (new Random().nextDouble()) - 0.1;
            array_bg[i] = a * (new Random().nextDouble()) - a / 2;
            array_bi[i] = a * (new Random().nextDouble()) - a / 2;
            array_bf[i] = a * (new Random().nextDouble()) - a / 2;
            array_bo[i] = a * (new Random().nextDouble()) - a / 2;
        }
        self.bg = array_bg;
        self.bi = array_bi;
        self.bf = array_bf;
        self.bo = array_bo;

        double array_wg_diff[][] = new double[mem_cell_ct][concat_len];
        double array_wi_diff[][] = new double[mem_cell_ct][concat_len];
        double array_wf_diff[][] = new double[mem_cell_ct][concat_len];
        double array_wo_diff[][] = new double[mem_cell_ct][concat_len];
        self.wg_diff = array_wg_diff;
        self.wi_diff = array_wi_diff;
        self.wf_diff = array_wf_diff;
        self.wo_diff = array_wo_diff;

        double array_bg_diff[] = new double[mem_cell_ct];
        double array_bi_diff[] = new double[mem_cell_ct];
        double array_bf_diff[] = new double[mem_cell_ct];
        double array_bo_diff[] = new double[mem_cell_ct];

        self.bg_diff = array_bg_diff;
        self.bi_diff = array_bi_diff;
        self.bf_diff = array_bf_diff;
        self.bo_diff = array_bo_diff;

    }

    public void apply_diff(Self self, double lr) {
        // TODO Auto-generated method stub

        for (int i = 0; i < self.wg.length; i++) {
            for (int j = 0; j < self.wg[0].length; j++) {
                self.wg[i][j] = self.wg[i][j] - lr * self.wg_diff[i][j];
                self.wi[i][j] = self.wi[i][j] - lr * self.wi_diff[i][j];
                self.wf[i][j] = self.wf[i][j] - lr * self.wf_diff[i][j];
                self.wo[i][j] = self.wo[i][j] - lr * self.wo_diff[i][j];
            }
            self.bg[i] = self.bg[i] - lr * self.bg_diff[i];
            self.bi[i] = self.bi[i] - lr * self.bi_diff[i];
            self.bf[i] = self.bf[i] - lr * self.bf_diff[i];
            self.bo[i] = self.bo[i] - lr * self.bo_diff[i];

        }

        self.wg_diff = new double[self.wg.length][self.wg[0].length];
        self.wi_diff = new double[self.wi.length][self.wi[0].length];
        self.wf_diff = new double[self.wf.length][self.wf[0].length];
        self.wo_diff = new double[self.wo.length][self.wo[0].length];

        self.bg_diff = new double[self.wo.length];
        self.bi_diff = new double[self.wo.length];
        self.bf_diff = new double[self.wo.length];
        self.bo_diff = new double[self.wo.length];

    }

}
class LstmState {

    public void initialize(Self self, int ind) {
        // TODO Auto-generated method stub
        self.g[ind] = new double[self.men_cell_ct];
        self.i[ind] = new double[self.men_cell_ct];
        self.f[ind] = new double[self.men_cell_ct];
        self.o[ind] = new double[self.men_cell_ct];
        self.s[ind] = new double[self.men_cell_ct];
        self.h[ind] = new double[self.men_cell_ct];
        self.bottom_diff_h[ind] = new double[self.men_cell_ct];
        self.bottom_diff_s[ind] = new double[self.men_cell_ct];
        self.bottom_diff_x[ind] = new double[self.x_dim];
    }

}

class Self {
    static int men_cell_ct;
    int x_dim;
    double[][] wg;
    double[][] wi;
    double[][] wf;
    double[][] wo;

    double[] bg;
    double[] bi;
    double[] bf;
    double[] bo;

    double[][] wg_diff;
    double[][] wi_diff;
    double[][] wf_diff;
    double[][] wo_diff;

    double[] bg_diff;
    double[] bi_diff;
    double[] bf_diff;
    double[] bo_diff;

    double[][] g = new double[15][];;
    double[][] i = new double[15][];;
    double[][] f = new double[15][];;
    double[][] o = new double[15][];;
    double[][] s = new double[15][];
    double[][] h = new double[15][];
    double[][] bottom_diff_s = new double[15][];
    double[][] bottom_diff_h = new double[15][];
    double[][] bottom_diff_x = new double[15][]; // 此处要改
    double[][] s_prev = new double[15][];
    double[][] h_prev = new double[15][];
    double[] x;
    double[] xc;
    ArrayList<double[]> x_list = new ArrayList<double[]>();
    ArrayList<LstmNode> lstm_node_list = new ArrayList<LstmNode>();

    LstmState state;

    public static double[] Matrix_multiplication(double[][] Matrix_in, double[] Vector) {
        double[] Matrix_out = new double[Matrix_in.length];

        for (int i = 0; i < Matrix_in.length; i++) {
            for (int j = 0; j < Matrix_in[0].length; j++) {
                Matrix_out[i] += Matrix_in[i][j] * Vector[j];
            }

        }
        return Matrix_out;
    }

}

class ToylossLayer {

    public double loss(double[] pred, double label) {
        // TODO Auto-generated method stub
        return (pred[0] - label) * (pred[0] - label);
    }

    public double[] bottom_diff(double[] pred, double label) {
        // TODO Auto-generated method stub
        double[] diff = new double[pred.length];
        diff[0] = 2 * (pred[0] - label);
        return diff;
    }

}

class Box
{
    int CPU_left;
    int MEM_left;

    int[] ecs_buffer;
    Box(int CPU_left,int MEM_left,int ecs_num_for_predict)
    {
        this.CPU_left = CPU_left;
        this.MEM_left = MEM_left*1024;
        this.ecs_buffer = new int[ecs_num_for_predict];
        for(int i =0;i<ecs_num_for_predict;i++)
        {
            this.ecs_buffer[i] =0;
        }
    }
}
class flavor
{
    int flavor_count;
    int flavor_id;
    int cpu;
    int mem;
    //int flavor_num;
    flavor(int flavor_id,int cpu,int mem,int flavor_count)
    {
        this.flavor_id =flavor_id;//flavor1的1
        this.cpu = cpu;
        //this.flavor_num =flavor_num;//数量
        this.mem = mem;
        this.flavor_count =flavor_count;//顺序
    }
    public int getCpu() {
        return this.cpu;
    }

    public int getMem() {
        return this.mem;
    }


}

