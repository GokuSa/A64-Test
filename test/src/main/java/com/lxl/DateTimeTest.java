package com.lxl;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateTimeTest {
    public static void main(String[] args) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        SimpleDateFormat simpleDateFormat2 = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        Date date = new Date();
        String current=simpleDateFormat2.format(date);
        String startDate="2017-02-14";
        String endDate="2017-02-14";
        String startTime=" 09:24:00";
        String endTime=" 09:39:00";
        try {
            //判读日期是否在指定范围
            Date start=simpleDateFormat.parse(startDate+" 00:00:00");
            Date end=simpleDateFormat.parse(endDate+" 23:59:59");
            boolean bwtween = date.after(start) && date.before(end);
            System.out.println("the result is "+bwtween);
            //判断时间与指定范围的关系
            Date timeStart=simpleDateFormat.parse(current+startTime);
            Date timeEnd=simpleDateFormat.parse(current+endTime);
            long margin=0;
            if(date.before(timeStart)){
                margin=timeStart.getTime()-date.getTime();
            } else if (date.after(start) && date.before(timeEnd)) {
                margin=timeEnd.getTime()-date.getTime();
            }else{
                margin=-1;
            }

            System.out.println(margin);

        } catch (ParseException e) {
            e.printStackTrace();
        }
    }
}
